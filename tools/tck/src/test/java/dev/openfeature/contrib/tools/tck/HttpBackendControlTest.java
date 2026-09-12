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
 * Guards the request sequence {@link HttpBackendControl} issues, which no scenario can assert.
 *
 * <p>Every scenario's isolation rests on {@link HttpBackendControl#prepareScenario()} choosing the
 * right endpoint against a backend that implements only part of the control API, and on a
 * disconnect being remembered until something ends it. Both are invisible from inside the Gherkin:
 * a control that reset nothing would leave each scenario running against whatever state the
 * previous one left behind, and the suite would report those results as conformance. A control that
 * reset a backend it had just stopped would register the next scenario's provider against a backend
 * that is still down, and report the failure as a provider defect.
 *
 * <p>So the control API is stubbed with the JDK's own {@link HttpServer} — no Docker, no
 * Testcontainers, nothing off loopback — and the requests actually sent are asserted, in order. The
 * three rules being pinned are normative in {@code openapi/control-api.yaml}: {@code /reset} is
 * preferred and {@code /start} is the documented fallback; the fallback is detected once per suite
 * and cached; and {@code /reset} is not specified to start a stopped backend, so the scenario after
 * a disconnect must use {@code /start}.
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

            // The preferred primitive, because it causes no availability blip: a /start between
            // scenarios restarts the backend, which the provider under test may legitimately
            // report as a lifecycle event in the scenario that follows.
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

            // Once per suite, not once per scenario: a wasted 404 before every scenario is a slow
            // suite, and never probing at all would mean a backend that grows /reset is never used
            // properly. This is the path flagd-testbed actually takes — its launchpad has no /reset.
            assertThat(stub.paths()).containsExactly("/reset", "/start", "/start", "/start");
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {404, 501})
    @DisplayName("both documented not-implemented statuses trigger the fallback")
    void bothNotImplementedStatusesFallBack(int status) throws Exception {
        try (StubControlApi stub = StubControlApi.start().answering("/reset", status)) {
            // control-api.yaml permits either, so neither may be treated as a failed control call.
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

            // /reset restores flag state and is explicitly not specified to start a stopped
            // backend. Without this the next scenario would prepare a backend that is still down.
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

            // A scenario that ended its own outage leaves the backend up, so the blip-free
            // primitive is available again and the next scenario should not pay for a restart.
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

            // /restart is optional in control-api.yaml and no shipped scenario reaches it: the
            // disconnect/reconnect scenario is an unbounded outage asserted in two steps, because
            // a self-healing restart races the stale assertion. Asserted over the wire rather than
            // by reflection, so reinstating a caller cannot slip past by using a different name.
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

            // A control call that did nothing would leave the scenario in an unknown state and its
            // assertions would then be measuring the previous scenario's backend.
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

        // The control API must stay reachable even while the backend is deliberately down, so this
        // is a broken stack rather than an outage, and it has to read that way.
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

            // The readiness check is what replaced the old post-command settle: it probes the thing
            // whose readiness is in question, so a slow control API is waited for and a dead one is
            // reported, and neither costs a fixed pause.
            assertThat(stub.requests()).containsExactly("GET /healthz");
        }
    }

    @Test
    @DisplayName("awaitReady treats an unimplemented /healthz as ready")
    void awaitReadyAcceptsNotImplemented() throws Exception {
        try (StubControlApi stub = StubControlApi.start().answering("/healthz", 404)) {
            // 404 is "not implemented", which control-api.yaml defines as ready: readiness then
            // rests on the control port accepting a connection, already established by the Compose
            // wait strategy. flagd-testbed's launchpad serves no /healthz, so this is the normal
            // path rather than an edge case.
            new HttpBackendControl(stub.baseUrl(), "default").awaitReady(PROBE_BUDGET);

            assertThat(stub.paths()).containsExactly("/healthz");
        }
    }

    @Test
    @DisplayName("awaitReady keeps probing while the control API says not yet")
    void awaitReadyRetriesNotReady() throws Exception {
        try (StubControlApi stub = StubControlApi.start().scripting("/healthz", 503, 503, 200)) {
            new HttpBackendControl(stub.baseUrl(), "default").awaitReady(Duration.ofSeconds(10));

            // 503 is the control API saying "not ready", so it is retried rather than accepted.
            assertThat(stub.paths()).containsExactly("/healthz", "/healthz", "/healthz");
        }
    }

    @Test
    @DisplayName("awaitReady gives up reporting what the last probe actually saw")
    void awaitReadyReportsTheLastProbe() throws Exception {
        try (StubControlApi stub = StubControlApi.start().answering("/healthz", 503)) {
            HttpBackendControl control = new HttpBackendControl(stub.baseUrl(), "default");

            // "did not become ready" alone sends an adopter to the wrong place: a refused
            // connection is a stack that never came up, a 503 is one that is up and not finished.
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

    /**
     * A control API that records every request it received and answers a scripted status.
     *
     * <p>Bound to the loopback interface on an ephemeral port, so a test never contends for a
     * fixed port and never leaves the machine.
     */
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

        /**
         * Binds and immediately closes a server, so the returned address is almost certainly not
         * listening. Used for the "the stack is not up" cases.
         */
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

        /**
         * Answers the given statuses one per request to {@code path}, which is how "not ready, then
         * ready" is expressed. The last one repeats once the script is exhausted.
         */
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
            // The last entry repeats, so a script cannot run dry and turn into a 200 by accident.
            return script.size() > 1 ? script.poll() : script.peek();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
