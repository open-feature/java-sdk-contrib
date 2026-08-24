package dev.openfeature.contrib.tools.providertck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.plugin.event.Event;
import io.cucumber.plugin.event.EventHandler;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.Location;
import io.cucumber.plugin.event.Result;
import io.cucumber.plugin.event.Status;
import io.cucumber.plugin.event.TestCase;
import io.cucumber.plugin.event.TestCaseFinished;
import io.cucumber.plugin.event.TestRunFinished;
import io.cucumber.plugin.event.TestStep;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.TestAbortedException;

/**
 * The conformance report exists to make one rule checkable, so these tests check it.
 *
 * <p>Appendix F requires that a scenario skipped for an undeclared capability is reported as skipped
 * with the reason and never as passed. The Go TCK shipped a version of this emitter that broke the
 * rule silently — its runner did not deliver the capability-skip signal to the after-hook, so every
 * skipped scenario was recorded twice, once correctly and once as passed. The equivalent hazard in
 * Cucumber would be an event arriving more than once per scenario, or a hook's success masking the
 * abort, so the totals and the per-scenario outcomes are asserted directly rather than assumed.
 */
class ConformanceReportPluginTest {

    private static final Set<Capability> DECLARED = EnumSet.of(Capability.OBJECT, Capability.EVENTS);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("every scenario appears exactly once, and a skipped one is never called passed")
    void everyScenarioAppearsExactlyOnce(@TempDir Path dir) throws IOException {
        JsonNode report = run(dir, defaultScenarios());

        List<JsonNode> scenarios = new ArrayList<>();
        report.get("scenarios").forEach(scenarios::add);

        assertThat(scenarios).hasSize(defaultScenarios().size());
        assertThat(scenarios).extracting(s -> s.get("name").asText()).doesNotHaveDuplicates();

        Map<String, Long> byOutcome = new HashMap<>();
        for (JsonNode scenario : scenarios) {
            byOutcome.merge(scenario.get("outcome").asText(), 1L, Long::sum);
        }
        assertThat(byOutcome).containsOnly(entry("passed", 2L), entry("failed", 1L), entry("not-declared", 2L));
        assertThat(byOutcome.values().stream().mapToLong(Long::longValue).sum()).isEqualTo(scenarios.size());
    }

    @Test
    @DisplayName("a capability skip carries the reason it was skipped")
    void aCapabilitySkipCarriesItsReason(@TempDir Path dir) throws IOException {
        JsonNode report = run(dir, defaultScenarios());

        JsonNode stale = scenarioNamed(report, "stale scenario");
        assertThat(stale.get("outcome").asText()).isEqualTo("not-declared");
        assertThat(stale.get("reason").asText())
                .isEqualTo("requires capability @stale, which this provider does not declare");
        assertThat(stale.get("tags")).extracting(JsonNode::asText).contains("@stale");
    }

    @Test
    @DisplayName("every capability is reported, and only a declared one that failed reads as failed")
    void everyCapabilityIsReported(@TempDir Path dir) throws IOException {
        JsonNode capabilities = run(dir, defaultScenarios()).get("capabilities");

        for (Capability capability : Capability.values()) {
            JsonNode result = capabilities.get(capability.tag());
            assertThat(result)
                    .as(
                            "capability %s is missing; an absent one cannot be told apart from a forgotten one",
                            capability.tag())
                    .isNotNull();

            String state = result.get("state").asText();
            if (!DECLARED.contains(capability)) {
                assertThat(state).isEqualTo("not-declared");
            } else if (capability == Capability.EVENTS) {
                // The failing scenario carries @events, so the capability cannot read as passed.
                assertThat(state).isEqualTo("failed");
            } else {
                assertThat(state).isEqualTo("passed");
            }

            // The schema requires a reason for anything that did not pass, because a bare tag with
            // no explanation is not something a person comparing providers can act on.
            if ("passed".equals(state)) {
                assertThat(result.has("reason")).isFalse();
            } else {
                assertThat(result.get("reason").asText()).isNotEmpty();
            }
        }
    }

    @Test
    @DisplayName("a scenario that did not pass always says why")
    void everyNonPassingScenarioSaysWhy(@TempDir Path dir) throws IOException {
        for (JsonNode scenario : run(dir, defaultScenarios()).get("scenarios")) {
            if ("passed".equals(scenario.get("outcome").asText())) {
                assertThat(scenario.has("reason")).isFalse();
            } else {
                assertThat(scenario.get("reason").asText())
                        .as("scenario %s", scenario.get("name").asText())
                        .isNotEmpty();
            }
        }
    }

    @Test
    @DisplayName("the report carries everything the schema requires")
    void theReportCarriesWhatTheSchemaRequires(@TempDir Path dir) throws IOException {
        JsonNode report = run(dir, defaultScenarios());

        assertThat(report.get("schemaVersion").asText()).isEqualTo("1");
        assertThat(report.get("provider").get("name").asText()).isEqualTo("My Provider");
        assertThat(report.get("provider").get("language").asText()).isEqualTo("java");
        assertThat(report.get("provider").get("configuration").asText()).isEqualTo("my-provider-rpc");
        assertThat(report.get("sdk").get("name").asText()).isEqualTo("dev.openfeature:sdk");
        assertThat(report.get("sdk").get("version").asText()).isNotEmpty();
        assertThat(report.get("tck").get("implementation").asText()).isEqualTo("java-sdk-contrib/tools/provider-tck");
        assertThat(report.get("tck").get("specRevision").asText()).hasSizeGreaterThanOrEqualTo(7);
        assertThat(report.get("tck").get("assetsTree").asText()).matches("[0-9a-f]{40}");
        assertThat(report.get("backend").get("controlApi").asText()).isEqualTo("http");
    }

    @Test
    @DisplayName("the SDK version is read from the classpath rather than declared")
    void theSdkVersionIsRead() {
        assertThat(TckBuildInfo.sdkVersion())
                .as("the OpenFeature SDK is on the test classpath, so its version must be discoverable")
                .isNotEqualTo(TckBuildInfo.UNKNOWN)
                .matches("\\d+\\.\\d+.*");
    }

    @Test
    @DisplayName("nothing is written when no report directory is configured")
    void nothingIsWrittenByDefault(@TempDir Path dir) throws IOException {
        FakeEventPublisher publisher = new FakeEventPublisher();
        new ConformanceReportPlugin(() -> null, () -> Optional.of(metadata())).setEventPublisher(publisher);
        publisher.emit(finished(scenario("evaluation", "a scenario", "@object"), Status.PASSED, null));
        publisher.emit(new TestRunFinished(Instant.now(), new Result(Status.PASSED, Duration.ZERO, null)));

        try (Stream<Path> written = Files.list(dir)) {
            assertThat(written).isEmpty();
        }
    }

    @Test
    @DisplayName("the report is named after the configuration, safely")
    void theReportIsNamedAfterTheConfiguration() {
        assertThat(ReportNames.configurationOf(MyProviderRpcTckTest.class)).isEqualTo("my-provider-rpc");
        assertThat(ReportNames.fileNameOf("flagd-rpc")).isEqualTo("flagd-rpc.json");
        assertThat(ReportNames.fileNameOf("flagd/rpc"))
                .as("a configuration name is chosen to read well, not to be path-safe")
                .isEqualTo("flagd-rpc.json");
        assertThat(ReportNames.fileNameOf("../escape")).isEqualTo("escape.json");
    }

    /** A suite whose name the default configuration derivation has to cope with. */
    private static final class MyProviderRpcTckTest {}

    private JsonNode run(Path dir, List<TestCaseFinished> events) throws IOException {
        FakeEventPublisher publisher = new FakeEventPublisher();
        new ConformanceReportPlugin(dir::toString, () -> Optional.of(metadata())).setEventPublisher(publisher);
        events.forEach(publisher::emit);
        publisher.emit(new TestRunFinished(Instant.now(), new Result(Status.FAILED, Duration.ZERO, null)));

        Path written = dir.resolve("my-provider-rpc.json");
        assertThat(written).exists();
        return MAPPER.readTree(Files.readAllBytes(written));
    }

    private static TckRunMetadata metadata() {
        TckRunMetadata metadata = new TckRunMetadata("my-provider-rpc", DECLARED, "a test double", "http");
        metadata.recordProviderName("My Provider");
        return metadata;
    }

    /**
     * The scenarios every test works from: two that pass, one that fails, two skipped for a
     * capability the harness did not declare.
     */
    private static List<TestCaseFinished> defaultScenarios() {
        return Arrays.asList(
                finished(scenario("evaluation", "a plain scenario"), Status.PASSED, null),
                finished(scenario("evaluation", "an object scenario", "@object"), Status.PASSED, null),
                finished(
                        scenario("events", "an event scenario", "@events"),
                        Status.FAILED,
                        new AssertionError("expected PROVIDER_READY")),
                finished(
                        scenario("events", "stale scenario", "@events", "@stale"),
                        Status.SKIPPED,
                        new TestAbortedException("Skipped: provider does not declare capability STALE")),
                finished(
                        scenario("lifecycle", "a lifecycle scenario", "@lifecycle"),
                        Status.SKIPPED,
                        new TestAbortedException("Skipped: provider does not declare capability LIFECYCLE")));
    }

    private static TestCaseFinished finished(TestCase testCase, Status status, Throwable error) {
        return new TestCaseFinished(Instant.now(), testCase, new Result(status, Duration.ofMillis(12), error));
    }

    private static TestCase scenario(String feature, String name, String... tags) {
        return new FakeTestCase(URI.create("classpath:features/" + feature + ".feature"), name, Arrays.asList(tags));
    }

    private static JsonNode scenarioNamed(JsonNode report, String name) {
        for (JsonNode scenario : report.get("scenarios")) {
            if (name.equals(scenario.get("name").asText())) {
                return scenario;
            }
        }
        throw new AssertionError("no scenario named " + name + " in the report");
    }

    /** Collects the plugin's handlers so a test can drive them directly. */
    private static final class FakeEventPublisher implements EventPublisher {

        private final Map<Class<?>, List<EventHandler<?>>> handlers = new HashMap<>();

        @Override
        public <T> void registerHandlerFor(Class<T> eventType, EventHandler<T> handler) {
            handlers.computeIfAbsent(eventType, key -> new ArrayList<>()).add(handler);
        }

        @Override
        public <T> void removeHandlerFor(Class<T> eventType, EventHandler<T> handler) {
            handlers.getOrDefault(eventType, Collections.emptyList()).remove(handler);
        }

        @SuppressWarnings("unchecked")
        <T extends Event> void emit(T event) {
            for (EventHandler<?> handler : handlers.getOrDefault(event.getClass(), Collections.emptyList())) {
                ((EventHandler<T>) handler).receive(event);
            }
        }
    }

    /** The parts of a Cucumber test case the report reads, and nothing else. */
    private static final class FakeTestCase implements TestCase {

        private final URI uri;
        private final String name;
        private final List<String> tags;

        FakeTestCase(URI uri, String name, List<String> tags) {
            this.uri = uri;
            this.name = name;
            this.tags = tags;
        }

        @Override
        public Integer getLine() {
            return 1;
        }

        @Override
        public Location getLocation() {
            return new Location(1, 1);
        }

        @Override
        public String getKeyword() {
            return "Scenario";
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getScenarioDesignation() {
            return uri + ":1";
        }

        @Override
        public List<String> getTags() {
            return tags;
        }

        @Override
        public List<TestStep> getTestSteps() {
            return Collections.emptyList();
        }

        @Override
        public URI getUri() {
            return uri;
        }

        @Override
        public UUID getId() {
            return UUID.randomUUID();
        }
    }
}
