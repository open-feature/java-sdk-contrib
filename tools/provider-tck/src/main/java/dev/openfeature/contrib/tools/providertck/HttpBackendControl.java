package dev.openfeature.contrib.tools.providertck;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link BackendControl} backed by the standardised HTTP control API.
 *
 * <p>This is the normative implementation for every provider that talks to an external backend. It
 * implements the contract in {@code openapi/control-api.yaml}, including the documented fallback
 * for the optional {@code /reset} operation. It uses the JDK HTTP client so that adopting the TCK
 * does not drag an HTTP library onto a provider's test classpath.
 *
 * <p>Every operation here manipulates the backend <em>process</em> or its flag state. None of them
 * touch containers — that is the no-container-restart invariant, and it is the reason a provider
 * built once at suite start stays valid for every scenario.
 *
 * <p>Constructed by {@link ContainerizedProviderTckTest} once the Compose stack is up and the
 * control API host port is known. Provider authors do not build one themselves.
 */
public final class HttpBackendControl implements BackendControl {

    private static final Logger log = LoggerFactory.getLogger(HttpBackendControl.class);

    private final HttpClient http;
    private final String baseUrl;
    private final String defaultConfig;
    private final Duration settleTime;

    /**
     * Tri-state cache of whether the backend implements the optional {@code /reset} operation.
     * {@code null} until the first {@link #reset()} call probes it.
     */
    private Boolean resetSupported;

    /**
     * Whether the backend was last known to be unreachable. Conservative: {@link #disconnectFor}
     * sets it even though the backend comes back on its own, because a scenario may end before it
     * does.
     */
    private boolean backendStopped;

    /**
     * Creates a control client for a running backend.
     *
     * @param baseUrl the control API base URL, without a trailing slash
     * @param defaultConfig the configuration name defining the canonical baseline
     * @param settleTime how long to pause after a command before continuing
     */
    HttpBackendControl(String baseUrl, String defaultConfig, Duration settleTime) {
        this.baseUrl = baseUrl;
        this.defaultConfig = defaultConfig;
        this.settleTime = settleTime;
        this.http =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    /**
     * Returns the base URL of the control API, for diagnostics.
     *
     * @return the control API base URL
     */
    public String baseUrl() {
        return baseUrl;
    }

    @Override
    public String description() {
        return "HTTP control API at " + baseUrl;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Always {@link BackendControl#CONTROL_API_HTTP}: this is the normative control API, and a
     * run conducted through it is the portable kind of conformance claim.
     */
    @Override
    public String controlApi() {
        return CONTROL_API_HTTP;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Prefers {@code POST /reset} when the backend is already running, because restoring the
     * baseline without an availability blip means the previous scenario teardown cannot leak a
     * spurious lifecycle event into the next scenario. When the previous scenario left the backend
     * unreachable, {@code /reset} alone would not bring it back, so this falls through to
     * {@code POST /start?config=...}.
     */
    @Override
    public void prepareScenario() {
        if (backendStopped) {
            start();
        } else {
            reset();
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Issues {@code POST /change}.
     */
    @Override
    public void changeFlag() {
        post("/change");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Issues {@code POST /stop}, which makes the backend unreachable without stopping its
     * container.
     */
    @Override
    public void disconnect() {
        post("/stop");
        backendStopped = true;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Issues {@code POST /start?config=...}, which also restores the baseline flag state.
     */
    @Override
    public void reconnect() {
        start();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Issues {@code POST /restart?seconds=...}. Flag state is preserved across the outage, so a
     * provider observes an availability change and not a configuration change. The control API
     * takes whole seconds, so a sub-second outage is rounded up to one second.
     */
    @Override
    public void disconnectFor(Duration outage) {
        int seconds = (int) Math.max(1, Math.ceil(outage.toMillis() / 1000.0));
        post("/restart?seconds=" + seconds);
        backendStopped = true;
    }

    /**
     * Waits until the control API accepts commands.
     *
     * <p>Probes the optional {@code GET /healthz}. A {@code 404} is a conformant answer meaning
     * "not implemented", in which case readiness has already been established by the Testcontainers
     * listening-port wait strategy and this returns immediately.
     *
     * @param timeout how long to keep probing
     */
    public void awaitReady(Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        RuntimeException last = null;
        while (System.nanoTime() < deadline) {
            try {
                HttpResponse<Void> response = http.send(
                        HttpRequest.newBuilder(URI.create(baseUrl + "/healthz"))
                                .GET()
                                .timeout(Duration.ofSeconds(5))
                                .build(),
                        HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() == 200 || response.statusCode() == 404) {
                    return;
                }
                last = new IllegalStateException("control API not ready, HTTP " + response.statusCode());
            } catch (IOException e) {
                last = new IllegalStateException("control API not reachable at " + baseUrl, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while waiting for the control API", e);
            }
            sleep(Duration.ofMillis(200));
        }
        throw new IllegalStateException("control API at " + baseUrl + " did not become ready within " + timeout, last);
    }

    /**
     * Starts the backend with the default configuration, seeding flag state to that configuration
     * baseline.
     */
    private void start() {
        post("/start?config=" + defaultConfig);
        backendStopped = false;
    }

    /**
     * Restores flag state to the seeded baseline without an availability blip.
     *
     * <p>{@code POST /reset} is optional. When the backend answers {@code 404} or {@code 501} the
     * result is cached and every subsequent call falls back to {@code POST /start?config=...},
     * which resets state at the cost of a process restart. Both paths are conformant; see
     * {@code openapi/control-api.yaml}.
     */
    private void reset() {
        if (Boolean.FALSE.equals(resetSupported)) {
            start();
            return;
        }
        HttpResponse<Void> response = send("/reset");
        if (response.statusCode() == 404 || response.statusCode() == 501) {
            if (resetSupported == null) {
                log.info(
                        "Control API at {} does not implement POST /reset (HTTP {}); "
                                + "falling back to POST /start?config={} for scenario isolation.",
                        baseUrl,
                        response.statusCode(),
                        defaultConfig);
            }
            resetSupported = false;
            start();
            return;
        }
        expectSuccess("/reset", response);
        resetSupported = true;
        settle();
    }

    private void post(String path) {
        expectSuccess(path, send(path));
        settle();
    }

    private HttpResponse<Void> send(String path) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .POST(HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(30))
                .build();
        try {
            return http.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (IOException e) {
            throw new IllegalStateException("control API call POST " + baseUrl + path + " failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted during control API call POST " + path, e);
        }
    }

    private void expectSuccess(String path, HttpResponse<Void> response) {
        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                    "control API call POST " + baseUrl + path + " returned HTTP " + response.statusCode()
                            + ", expected 200. See openapi/control-api.yaml for the expected contract.");
        }
    }

    private void settle() {
        sleep(settleTime);
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for the backend to settle", e);
        }
    }
}
