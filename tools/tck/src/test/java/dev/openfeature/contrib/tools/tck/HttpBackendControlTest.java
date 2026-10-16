package dev.openfeature.contrib.tools.tck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Asserts the request sequence {@link HttpBackendControl} sends, against a JDK {@link HttpServer}
 * stub, per the rules in {@code openapi/control-api.yaml}.
 */
class HttpBackendControlTest {

    private static final Duration PROBE_BUDGET = Duration.ofSeconds(5);

    @Test
    @DisplayName("prepareScenario uses /reset for every scenario when the backend implements it")
    void prefersReset() throws Exception {
        try (StubControlApi stub = StubControlApi.start()) {
            HttpBackendControl control = new HttpBackendControl(stub.baseUrl(), "default");

            control.prepareScenario();
            control.prepareScenario();

            // preferred; /start would cause an availability blip
            assertThat(stub.paths()).containsExactly("/reset", "/reset");
        }
    }

    @Test
    @DisplayName("an unimplemented /reset is probed once, then every scenario falls back to /start")
    void fallsBackToStartAndCachesTheAnswer() throws Exception {
        try (StubControlApi stub = StubControlApi.start().answering("/reset", 404)) {
            HttpBackendControl control = new HttpBackendControl(stub.baseUrl(), "default");

            control.prepareScenario();
            control.prepareScenario();
            control.prepareScenario();

            // probed once per suite; flagd-testbed has no /reset
            assertThat(stub.paths()).containsExactly("/reset", "/start", "/start", "/start");
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {404, 501})
    @DisplayName("both documented not-implemented statuses trigger the fallback")
    void bothNotImplementedStatusesFallBack(int status) throws Exception {
        try (StubControlApi stub = StubControlApi.start().answering("/reset", status)) {
            // control-api.yaml permits either
            new HttpBackendControl(stub.baseUrl(), "default").prepareScenario();

            assertThat(stub.paths()).containsExactly("/reset", "/start");
        }
    }

    @Test
    @DisplayName("the scenario after a disconnect starts the backend rather than resetting it")
    void aDisconnectForcesStart() throws Exception {
        try (StubControlApi stub = StubControlApi.start()) {
            HttpBackendControl control = new HttpBackendControl(stub.baseUrl(), "default");
            control.prepareScenario(); // settles on /reset, which this stub implements
            stub.forget();

            control.disconnect();
            control.prepareScenario();

            // /reset is not specified to start a stopped backend
            assertThat(stub.paths()).containsExactly("/stop", "/start");
        }
    }

    @Test
    @DisplayName("reconnecting clears the disconnect, so the next scenario resets again")
    void reconnectClearsTheDisconnect() throws Exception {
        try (StubControlApi stub = StubControlApi.start()) {
            HttpBackendControl control = new HttpBackendControl(stub.baseUrl(), "default");
            control.prepareScenario();
            control.disconnect();
            control.reconnect();
            stub.forget();

            control.prepareScenario();

            // backend is up again, so /reset suffices
            assertThat(stub.paths()).containsExactly("/reset");
        }
    }

    @Test
    @DisplayName("the fallback and the reconnect both name the configuration under test")
    void startNamesTheBackendConfiguration() throws Exception {
        try (StubControlApi stub = StubControlApi.start().answering("/reset", 404)) {
            new HttpBackendControl(stub.baseUrl(), "ssl").prepareScenario();

            assertThat(stub.requests()).containsExactly("POST /reset", "POST /start?config=ssl");
        }
    }

    @Test
    @DisplayName("no operation ever reaches /restart")
    void nothingCallsRestart() throws Exception {
        try (StubControlApi stub = StubControlApi.start()) {
            HttpBackendControl control = new HttpBackendControl(stub.baseUrl(), "default");

            control.prepareScenario();
            control.changeFlag();
            control.disconnect();
            control.reconnect();
            control.prepareScenario();

            // /restart is optional and unused; asserted over the wire
            assertThat(stub.paths()).doesNotContain("/restart");
        }
    }

    @Test
    @DisplayName("changeFlag posts /change")
    void changeFlagPostsChange() throws Exception {
        try (StubControlApi stub = StubControlApi.start()) {
            new HttpBackendControl(stub.baseUrl(), "default").changeFlag();

            assertThat(stub.paths()).containsExactly("/change");
        }
    }

    @Test
    @DisplayName("an unexpected status fails loudly instead of passing silently")
    void anUnexpectedStatusThrows() throws Exception {
        try (StubControlApi stub = StubControlApi.start().answering("/change", 500)) {
            HttpBackendControl control = new HttpBackendControl(stub.baseUrl(), "default");

            // a silent no-op would leave the scenario in an unknown state
            assertThatThrownBy(control::changeFlag)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("HTTP 500")
                    .hasMessageContaining("control-api.yaml");
        }
    }

    @Test
    @DisplayName("an unreachable control API says which call failed and where")
    void anUnreachableControlApiThrows() throws Exception {
        String baseUrl = StubControlApi.addressOfAClosedServer();
        HttpBackendControl control = new HttpBackendControl(baseUrl, "default");

        // an unreachable control API is a broken stack, not an outage
        assertThatThrownBy(control::changeFlag)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("POST " + baseUrl + "/change");
    }

    @Test
    @DisplayName("baseUrl is reportable without rebuilding it")
    void baseUrlIsReportable() throws Exception {
        try (StubControlApi stub = StubControlApi.start()) {
            HttpBackendControl control = new HttpBackendControl(stub.baseUrl(), "default");

            assertThat(control.baseUrl()).isEqualTo(stub.baseUrl());
            assertThat(control.description()).contains(stub.baseUrl());
            assertThat(control.controlApi()).isEqualTo(ControlApi.HTTP);
        }
    }

    // -- awaitReady --------------------------------------------------------------------------

    @Test
    @DisplayName("awaitReady returns as soon as /healthz answers, without sleeping first")
    void awaitReadyReturnsOnTheFirstAnswer() throws Exception {
        try (StubControlApi stub = StubControlApi.start()) {
            new HttpBackendControl(stub.baseUrl(), "default").awaitReady(PROBE_BUDGET);

            assertThat(stub.requests()).containsExactly("GET /healthz");
        }
    }

    @Test
    @DisplayName("awaitReady treats an unimplemented /healthz as ready")
    void awaitReadyAcceptsNotImplemented() throws Exception {
        try (StubControlApi stub = StubControlApi.start().answering("/healthz", 404)) {
            // 404 means not implemented, which control-api.yaml defines as ready
            new HttpBackendControl(stub.baseUrl(), "default").awaitReady(PROBE_BUDGET);

            assertThat(stub.paths()).containsExactly("/healthz");
        }
    }

    @Test
    @DisplayName("awaitReady keeps probing while the control API says not yet")
    void awaitReadyRetriesNotReady() throws Exception {
        try (StubControlApi stub = StubControlApi.start().scripting("/healthz", 503, 503, 200)) {
            new HttpBackendControl(stub.baseUrl(), "default").awaitReady(Duration.ofSeconds(10));

            // 503 means not ready, so retry
            assertThat(stub.paths()).containsExactly("/healthz", "/healthz", "/healthz");
        }
    }

    @Test
    @DisplayName("awaitReady gives up reporting what the last probe actually saw")
    void awaitReadyReportsTheLastProbe() throws Exception {
        try (StubControlApi stub = StubControlApi.start().answering("/healthz", 503)) {
            HttpBackendControl control = new HttpBackendControl(stub.baseUrl(), "default");

            // distinguish refused connection from 503
            assertThatThrownBy(() -> control.awaitReady(Duration.ofMillis(300)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("did not become ready")
                    .hasRootCauseMessage("control API not ready, HTTP 503");
        }
    }

    @Test
    @DisplayName("awaitReady reports a control API that is not there at all")
    void awaitReadyReportsAnAbsentControlApi() throws Exception {
        String baseUrl = StubControlApi.addressOfAClosedServer();
        HttpBackendControl control = new HttpBackendControl(baseUrl, "default");

        assertThatThrownBy(() -> control.awaitReady(Duration.ofMillis(300)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("did not become ready")
                .hasRootCauseInstanceOf(IOException.class);
    }

    /** A loopback control API stub that records requests and answers scripted statuses. */
    private static final class StubControlApi implements AutoCloseable {

        private final HttpServer server;
        private final List<String> requests = Collections.synchronizedList(new ArrayList<>());
        private final Map<String, Integer> statuses = new HashMap<>();
        private final Map<String, Deque<Integer>> scripted = new HashMap<>();

        private StubControlApi(HttpServer server) {
            this.server = server;
        }

        static StubControlApi start() throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            StubControlApi stub = new StubControlApi(server);
            server.createContext("/", stub::answer);
            server.start();
            return stub;
        }

        /** An address that is almost certainly not listening. */
        static String addressOfAClosedServer() throws IOException {
            try (StubControlApi stub = start()) {
                return stub.baseUrl();
            }
        }

        /** Answers {@code status} for every request to {@code path}. */
        StubControlApi answering(String path, int status) {
            statuses.put(path, status);
            return this;
        }

        /** Answers the given statuses in order for {@code path}; the last one repeats. */
        StubControlApi scripting(String path, int... sequence) {
            Deque<Integer> queue = new ArrayDeque<>();
            for (int status : sequence) {
                queue.add(status);
            }
            scripted.put(path, queue);
            return this;
        }

        String baseUrl() {
            return "http://" + server.getAddress().getHostString() + ":"
                    + server.getAddress().getPort();
        }

        /** Every request as {@code METHOD path[?query]}, in the order received. */
        List<String> requests() {
            synchronized (requests) {
                return new ArrayList<>(requests);
            }
        }

        /** Every request's path, dropping the method and the query string. */
        List<String> paths() {
            return requests().stream()
                    .map(request -> request.substring(request.indexOf(' ') + 1))
                    .map(target -> target.contains("?") ? target.substring(0, target.indexOf('?')) : target)
                    .collect(Collectors.toList());
        }

        /** Drops what has been recorded, so a test can assert only the part it set up. */
        void forget() {
            requests.clear();
        }

        private void answer(HttpExchange exchange) {
            try {
                String path = exchange.getRequestURI().getPath();
                String query = exchange.getRequestURI().getQuery();
                requests.add(exchange.getRequestMethod() + " " + path + (query == null ? "" : "?" + query));

                int status = nextStatus(path);

                byte[] body = "{\"status\":\"stub\"}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } finally {
                exchange.close();
            }
        }

        /** The scripted status for this path, or the fixed one, defaulting to 200. */
        private synchronized int nextStatus(String path) {
            Deque<Integer> script = scripted.get(path);
            if (script == null) {
                return statuses.getOrDefault(path, 200);
            }
            // last entry repeats so the script never runs dry
            return script.size() > 1 ? script.poll() : script.peek();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
