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
 * Client for the standardised backend control API.
 *
 * <p>Implements the contract in {@code openapi/control-api.yaml}, including the documented fallback
 * for the optional {@code /reset} operation. Uses the JDK HTTP client so that adopting the TCK does
 * not drag an HTTP library onto a provider's test classpath.
 *
 * <p>Every operation here manipulates the backend <em>process</em> or its flag state. None of them
 * touch containers — that is the no-container-restart invariant, and it is the reason a provider
 * built once at suite start stays valid for every scenario.
 */
public final class ControlApiClient {

    private static final Logger log = LoggerFactory.getLogger(ControlApiClient.class);

    private final HttpClient http;
    private final String baseUrl;
    private final Duration settleTime;

    /**
     * Tri-state cache of whether the backend implements the optional {@code /reset} operation.
     * {@code null} until the first {@link #reset(String)} call probes it.
     */
    private Boolean resetSupported;

    /**
     * Whether the backend was last known to be unreachable. Conservative: {@code restart} sets it
     * even though the backend comes back on its own, because a scenario may end before it does.
     */
    private boolean backendStopped;

    ControlApiClient(String baseUrl, Duration settleTime) {
        this.baseUrl = baseUrl;
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

    /**
     * Returns how the backend was driven, for the conformance report.
     *
     * <p>{@code http} is the normative control API and the only one this TCK implements. The schema
     * also allows {@code in-process}, a narrow allowance for providers with no backend at all; a
     * report claiming it for a provider that has one should be treated with suspicion.
     *
     * @return the control API kind, always {@code http}
     */
    public String controlApi() {
        return "http";
    }

    /**
     * Starts the backend with a named configuration, seeding flag state to that configuration's
     * baseline.
     *
     * @param config the configuration name
     */
    public void start(String config) {
        post("/start?config=" + config);
        backendStopped = false;
    }

    /**
     * Makes the backend unreachable without stopping its container.
     */
    public void stop() {
        post("/stop");
        backendStopped = true;
    }

    /**
     * Makes the backend unreachable for a bounded duration, then starts it again.
     *
     * <p>Flag state is preserved across the outage, so a provider observes an availability change
     * and not a configuration change.
     *
     * @param seconds how long the backend stays unreachable
     */
    public void restart(int seconds) {
        post("/restart?seconds=" + seconds);
        backendStopped = true;
    }

    /**
     * Puts the backend into the state every scenario starts from: running, with flag state at the
     * baseline of the default configuration.
     *
     * <p>Prefers {@link #reset(String)} when the backend is already running, because restoring the
     * baseline without an availability blip means the previous scenario's teardown cannot leak a
     * spurious lifecycle event into the next scenario. When the previous scenario left the backend
     * unreachable, {@code /reset} alone would not bring it back, so this falls through to
     * {@link #start(String)}.
     *
     * @param defaultConfig the configuration name defining the baseline
     */
    public void prepareScenario(String defaultConfig) {
        if (backendStopped) {
            start(defaultConfig);
        } else {
            reset(defaultConfig);
        }
    }

    /**
     * Mutates flag configuration so that a conforming provider observes a configuration change and
     * resolves a different value for {@code changing-flag} afterwards.
     */
    public void change() {
        post("/change");
    }

    /**
     * Restores flag state to the seeded baseline for scenario isolation.
     *
     * <p>Prefers the optional {@code POST /reset}, which causes no availability blip. When the
     * backend answers {@code 404} or {@code 501} the result is cached and every subsequent call
     * falls back to {@code POST /start?config=...}, which resets state at the cost of a process
     * restart. Both paths are conformant; see {@code openapi/control-api.yaml}.
     *
     * @param defaultConfig the configuration name to fall back to
     */
    public void reset(String defaultConfig) {
        if (Boolean.FALSE.equals(resetSupported)) {
            start(defaultConfig);
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
            start(defaultConfig);
            return;
        }
        expectSuccess("/reset", response);
        resetSupported = true;
        settle();
    }

    /**
     * Waits until the control API accepts commands.
     *
     * <p>Probes the optional {@code GET /healthz}. A {@code 404} is a conformant answer meaning "not
     * implemented", in which case readiness has already been established by the Testcontainers
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
