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
import io.cucumber.plugin.event.TestSourceRead;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
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

    private static final URI IDENTITY_URI = URI.create("classpath:features/identity.feature");

    /**
     * A feature whose shape is the one the report has to cope with: an outline whose rows share a
     * name across two Examples tables, a plain scenario alongside it, and an outline gated on a
     * capability so that a skipped row can be checked as well as a passing one.
     *
     * <p>Line numbers are looked up from this text rather than written down, so editing it cannot
     * silently make a test assert about the wrong row.
     */
    private static final String IDENTITY_FEATURE = String.join(
            "\n",
            "Feature: Report identity",
            "",
            "  Scenario: An unknown flag key returns the code default",
            "    Given nothing in particular",
            "",
            "  Scenario Outline: Requesting the wrong type returns the code default",
            "    Given a <requested>-flag with key \"<key>\" and a default value \"<default>\"",
            "",
            "    Examples: a string flag requested as something else",
            "      | key         | requested | default |",
            "      | string-flag | Boolean   | false   |",
            "      | string-flag | Integer   | 1       |",
            "",
            "    Examples: a boolean flag requested as something else",
            "      | key          | requested | default  |",
            "      | boolean-flag | String    | fallback |",
            "",
            "  Scenario Outline: A gated outline",
            "    Given nothing in particular",
            "",
            "    @stale",
            "    Examples: gated by a tag on this block alone",
            "      | mode |",
            "      | one  |",
            "",
            "    Examples: not gated",
            "      | mode |",
            "      | two  |",
            "");

    private static final String OUTLINE = "Requesting the wrong type returns the code default";

    private static final String GATED_OUTLINE = "A gated outline";

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
                            "capability %s is missing; a capability is left out only when it is declared and "
                                    + "no scenario exercises it, which is not the case here",
                            capability.tag())
                    .isNotNull();

            String state = result.get("state").asText();
            if (!DECLARED.contains(capability)) {
                assertThat(state).isEqualTo("not-declared");
            } else if (capability == Capability.EVENTS) {
                // The failing scenario carries @events, so the capability cannot read as passed.
                assertThat(state).isEqualTo("failed");
                assertThat(result.get("reason").asText())
                        .as("someone comparing providers wants to know how much failed before opening the detail")
                        .contains("1 of 1");
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

    @Test
    @DisplayName("each row of a Scenario Outline carries the Examples row it came from")
    void outlineRowsCarryTheirExample(@TempDir Path dir) throws IOException {
        JsonNode report = run(dir, identityScenarios());

        List<JsonNode> rows = scenariosNamed(report, OUTLINE);
        assertThat(rows)
                .as("three rows ran, so three entries are expected, all under the one outline name")
                .hasSize(3);

        assertThat(rows)
                .extracting(row -> MAPPER.convertValue(row.get("example"), Map.class))
                .containsExactly(
                        exampleOf("key", "string-flag", "requested", "Boolean", "default", "false"),
                        exampleOf("key", "string-flag", "requested", "Integer", "default", "1"),
                        exampleOf("key", "boolean-flag", "requested", "String", "default", "fallback"));
    }

    @Test
    @DisplayName("cell contents are reported verbatim as strings, because Gherkin has no types")
    void cellsAreNotCoerced(@TempDir Path dir) throws IOException {
        JsonNode example =
                scenariosNamed(run(dir, identityScenarios()), OUTLINE).get(1).get("example");

        assertThat(example.get("default").isTextual())
                .as("\"1\" is what the table said; a report that turns it into a number says something else")
                .isTrue();
        assertThat(example.get("default").asText()).isEqualTo("1");
    }

    @Test
    @DisplayName("a scenario that is not an outline row carries no example")
    void aPlainScenarioCarriesNoExample(@TempDir Path dir) throws IOException {
        JsonNode plain = scenarioNamed(run(dir, identityScenarios()), "An unknown flag key returns the code default");

        assertThat(plain.has("example"))
                .as("the field is omitted rather than emitted empty; the schema requires at least one property")
                .isFalse();
    }

    @Test
    @DisplayName("a row skipped for an undeclared capability still says which row it was")
    void aSkippedOutlineRowCarriesItsExample(@TempDir Path dir) throws IOException {
        List<JsonNode> rows = scenariosNamed(run(dir, identityScenarios()), GATED_OUTLINE);

        // Gherkin allows a tag on an individual Examples block, so two rows of one outline can
        // differ in whether the capability gate stops them. Both rows must appear, and each must
        // say which row it was: a skip that took its sibling with it would be invisible in the
        // totals, and a skip that cannot name its row is as ambiguous as a failure that cannot.
        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(row -> row.get("outcome").asText()).containsExactly("not-declared", "passed");
        assertThat(rows)
                .extracting(row -> MAPPER.convertValue(row.get("example"), Map.class))
                .containsExactly(exampleOf("mode", "one"), exampleOf("mode", "two"));
    }

    @Test
    @DisplayName("feature, name and example together identify a scenario uniquely")
    void scenariosAreUniquelyIdentified(@TempDir Path dir) throws IOException {
        JsonNode report = run(dir, identityScenarios());

        // Compared as tuples rather than as joined strings, so no separator can make two
        // distinct entries look alike or one entry look like two.
        List<List<String>> identities = new ArrayList<>();
        List<List<String>> namesOnly = new ArrayList<>();
        for (JsonNode scenario : report.get("scenarios")) {
            String feature = scenario.get("feature").asText();
            String name = scenario.get("name").asText();
            String example = scenario.has("example") ? scenario.get("example").toString() : "";
            namesOnly.add(Arrays.asList(feature, name));
            identities.add(Arrays.asList(feature, name, example));
        }

        assertThat(new HashSet<>(namesOnly))
                .as("the premise of this test: feature and name alone are not unique in this report")
                .hasSizeLessThan(namesOnly.size());
        assertThat(identities)
                .as("a consumer keying on feature, name and example must not lose an entry")
                .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("a declared capability no scenario exercises is omitted rather than called passed")
    void anUnexercisedCapabilityIsOmitted(@TempDir Path dir) throws IOException {
        Set<Capability> declared = EnumSet.of(Capability.OBJECT, Capability.TARGETING);
        JsonNode capabilities = run(dir, declared, defaultScenarios()).get("capabilities");

        assertThat(capabilities.has(Capability.TARGETING.tag()))
                .as(
                        "%s is declared and no scenario carries it; the suite asked no question, so it has "
                                + "no answer, and a green result there is a pass nothing examined",
                        Capability.TARGETING.tag())
                .isFalse();

        assertThat(capabilities.get(Capability.OBJECT.tag()).get("state").asText())
                .as("the omission must be specific, not a general failure to report capabilities")
                .isEqualTo("passed");
    }

    @Test
    @DisplayName("a declared capability whose every scenario was skipped is omitted too")
    void aCapabilityWhoseScenariosAllSkippedIsOmitted(@TempDir Path dir) throws IOException {
        // @events is declared; @stale is not. Every scenario in events.feature carries both — the
        // feature is tagged @events and each scenario adds @stale or @configuration-change — so
        // declaring @events alone runs none of them. Counting a capability as exercised because a
        // scenario carried its tag would report @events as passed here, which is the same vacuous
        // pass as a reserved capability arriving by a different route.
        Set<Capability> declared = EnumSet.of(Capability.OBJECT, Capability.EVENTS);
        List<TestCaseFinished> scenarios = Arrays.asList(
                finished(scenario("evaluation", "an object scenario", "@object"), Status.PASSED, null),
                finished(
                        scenario("events", "a stale scenario", "@events", "@stale"),
                        Status.SKIPPED,
                        new TestAbortedException("Skipped: provider does not declare capability STALE")));

        JsonNode capabilities = run(dir, declared, scenarios).get("capabilities");

        assertThat(capabilities.has(Capability.EVENTS.tag()))
                .as(
                        "%s was declared and no scenario carrying it ran, so nothing was demonstrated",
                        Capability.EVENTS.tag())
                .isFalse();
        assertThat(capabilities.get(Capability.OBJECT.tag()).get("state").asText())
                .isEqualTo("passed");
        assertThat(capabilities.get(Capability.STALE.tag()).get("state").asText())
                .as("the undeclared capability is still reported, with the reason it was not")
                .isEqualTo("not-declared");
    }

    /** A suite whose name the default configuration derivation has to cope with. */
    private static final class MyProviderRpcTckTest {}

    /**
     * The events the identity tests work from: the feature source Cucumber would publish, then one
     * test case per compiled pickle, each located on the line it came from.
     */
    private static List<Event> identityScenarios() {
        return Arrays.asList(
                new TestSourceRead(Instant.now(), IDENTITY_URI, IDENTITY_FEATURE),
                finished(
                        identity("An unknown flag key returns the code default", "  Scenario: An unknown flag"),
                        Status.PASSED,
                        null),
                finished(identity(OUTLINE, "| string-flag | Boolean"), Status.PASSED, null),
                finished(
                        identity(OUTLINE, "| string-flag | Integer"),
                        Status.FAILED,
                        new AssertionError("resolved 1 with no error code")),
                finished(identity(OUTLINE, "| boolean-flag | String"), Status.PASSED, null),
                finished(
                        identity(GATED_OUTLINE, "| one  |", "@stale"),
                        Status.SKIPPED,
                        new TestAbortedException("Skipped: provider does not declare capability STALE")),
                finished(identity(GATED_OUTLINE, "| two  |"), Status.PASSED, null));
    }

    private static TestCase identity(String name, String marker, String... tags) {
        return new FakeTestCase(IDENTITY_URI, name, Arrays.asList(tags), lineOf(marker));
    }

    /** Finds the 1-based line the given text sits on, so no test hard-codes a line number. */
    private static int lineOf(String marker) {
        String[] lines = IDENTITY_FEATURE.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains(marker)) {
                return i + 1;
            }
        }
        throw new AssertionError("no line of the test feature contains " + marker);
    }

    private static Map<String, String> exampleOf(String... keysAndValues) {
        Map<String, String> example = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            example.put(keysAndValues[i], keysAndValues[i + 1]);
        }
        return example;
    }

    private JsonNode run(Path dir, List<? extends Event> events) throws IOException {
        return run(dir, DECLARED, events);
    }

    private JsonNode run(Path dir, Set<Capability> declared, List<? extends Event> events) throws IOException {
        FakeEventPublisher publisher = new FakeEventPublisher();
        new ConformanceReportPlugin(dir::toString, () -> Optional.of(metadata(declared))).setEventPublisher(publisher);
        events.forEach(publisher::emit);
        publisher.emit(new TestRunFinished(Instant.now(), new Result(Status.FAILED, Duration.ZERO, null)));

        Path written = dir.resolve("my-provider-rpc.json");
        assertThat(written).exists();
        return MAPPER.readTree(Files.readAllBytes(written));
    }

    private static TckRunMetadata metadata() {
        return metadata(DECLARED);
    }

    private static TckRunMetadata metadata(Set<Capability> declared) {
        TckRunMetadata metadata = new TckRunMetadata("my-provider-rpc", declared, "a test double", "http");
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
        return new FakeTestCase(URI.create("classpath:features/" + feature + ".feature"), name, Arrays.asList(tags), 1);
    }

    private static JsonNode scenarioNamed(JsonNode report, String name) {
        for (JsonNode scenario : report.get("scenarios")) {
            if (name.equals(scenario.get("name").asText())) {
                return scenario;
            }
        }
        throw new AssertionError("no scenario named " + name + " in the report");
    }

    private static List<JsonNode> scenariosNamed(JsonNode report, String name) {
        List<JsonNode> matching = new ArrayList<>();
        for (JsonNode scenario : report.get("scenarios")) {
            if (name.equals(scenario.get("name").asText())) {
                matching.add(scenario);
            }
        }
        return matching;
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
        private final int line;

        FakeTestCase(URI uri, String name, List<String> tags, int line) {
            this.uri = uri;
            this.name = name;
            this.tags = tags;
            this.line = line;
        }

        @Override
        public Integer getLine() {
            return line;
        }

        /**
         * The pickle's location, which for an outline-derived test case is the Examples row rather
         * than the {@code Scenario Outline} line. That is what Cucumber reports, and it is the only
         * thing tying a compiled pickle back to the table it came from.
         */
        @Override
        public Location getLocation() {
            return new Location(line, 1);
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
            return uri + ":" + line;
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
