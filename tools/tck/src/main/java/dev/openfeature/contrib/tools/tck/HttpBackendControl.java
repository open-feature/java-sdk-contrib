package dev.openfeature.contrib.tools.tck;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link BackendControl} backed by the HTTP control API in {@code openapi/control-api.yaml}.
 *
 * <p>Constructed by {@link ContainerizedProviderTckTest}; never restarts containers.
 */
final class HttpBackendControl implements BackendControl {

    private static final Logger log = LoggerFactory.getLogger(HttpBackendControl.class);

    private static final int REQUEST_TIMEOUT_SECONDS = 30;

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS);

    private final HttpClient http;
    private final String baseUrl;
    private final String backendConfiguration;

    // whether optional /reset is supported; null until probed
    private Boolean resetSupported;

    // whether the backend was last left unreachable, so /reset alone won't suffice
    private boolean backendStopped;

    // a configuration one scenario asked for, which the next scenario has to be taken off again
    private String scenarioConfiguration;


    /**
     * Creates a control client for a running backend. No post-command settle; control calls return
     * once the backend has acted.
     *
     * @param baseUrl the control API base URL, without a trailing slash
     * @param backendConfiguration the backend configuration name defining the canonical baseline
     */
    HttpBackendControl(String baseUrl, String backendConfiguration) {
        this.baseUrl = baseUrl;
        this.backendConfiguration = backendConfiguration;
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
     * <p>Always {@link BackendType#HTTP}.
     */
    @Override
    public BackendType backendType() {
        return BackendType.HTTP;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Uses {@code POST /reset} if the backend is running on this suite's own configuration, else
     * {@code POST /start?config=...}: {@code /reset} restores flag state, not which configuration is
     * being served, so a scenario that served another one has to be taken off it explicitly.
     */
    @Override
    public void prepareScenario() {
        if (backendStopped || scenarioConfiguration != null) {
            start();
            // cleared only once the restart succeeded: a /start that failed leaves the scenario's
            // configuration serving, and /reset would not take the next scenario off it
            scenarioConfiguration = null;
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
     * <p>Issues {@code POST /stop}.
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
     * <p>Issues {@code POST /restart?seconds=...} and waits for the answer like any other call,
     * which is the point of the endpoint: a blocking implementation spends the outage inside the
     * response, and every language's HTTP client already knows how to wait for that. A timer here
     * would have to be written once per binding instead.
     *
     * <p>An outage is therefore expected to be seconds, comfortably inside the {@value #REQUEST_TIMEOUT_SECONDS}-second
     * request timeout every call shares; one approaching it would fail the request rather than the
     * scenario's assertion.
     */
    @Override
    public void disconnectFor(Duration outage) {
        post("/restart?seconds=" + outage.getSeconds());
        // Treated as still down, which costs the next scenario a /start instead of a /reset and
        // nothing else. The alternative is a timer: the response may arrive as soon as the outage
        // begins, and the specification is explicit that the TCK does not rely on it being delayed
        // -- a scenario awaits the provider's own events instead, and sleeping here to find out
        // whether the backend is back would put that timing in the control client, where this
        // endpoint exists precisely to avoid it.
        backendStopped = true;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Issues {@code POST /start?config=...} with the name given, which also seeds that
     * configuration's flag state.
     */
    @Override
    public void serveConfiguration(String configuration) {
        post("/start?config=" + URLEncoder.encode(configuration, StandardCharsets.UTF_8));
        scenarioConfiguration = configuration;
        backendStopped = false;
    }

    /**
     * Waits until the control API accepts commands, probing optional {@code GET /healthz} (a
     * {@code 404} counts as ready).
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

    /** Starts the backend, seeding the baseline flag state. */
    private void start() {
        post(
                backendConfiguration == null
                        ? "/start"
                        : "/start?config=" + URLEncoder.encode(backendConfiguration, StandardCharsets.UTF_8));
        backendStopped = false;
    }

    /** Restores baseline via {@code POST /reset}, falling back to {@code /start} on 404/501. */
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
                                + "falling back to POST /start?config={}.",
                        baseUrl,
                        response.statusCode(),
                        backendConfiguration);
            }
            resetSupported = false;
            start();
            return;
        }
        expectSuccess("/reset", response);
        resetSupported = true;
    }

    private void post(String path) {
        expectSuccess(path, send(path));
    }

    private HttpResponse<Void> send(String path) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .POST(HttpRequest.BodyPublishers.noBody())
                .timeout(REQUEST_TIMEOUT)
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

    /** Pauses between readiness probes. */
    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for the control API to become ready", e);
        }
    }
}
