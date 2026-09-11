package dev.openfeature.contrib.tools.providertck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.openfeature.contrib.tools.providertck.selftest.ReportSelfTestSteps;
import io.cucumber.junit.platform.engine.Constants;
import io.cucumber.plugin.event.EventHandler;
import io.cucumber.plugin.event.EventPublisher;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.EngineFilter;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

/**
 * The conformance report exists to make one rule checkable, so these tests check it.
 *
 * <p>Appendix F requires that a scenario skipped for an undeclared capability is reported as skipped
 * with the reason and never as passed. The Go TCK shipped an emitter that broke the rule silently —
 * its runner did not deliver the capability-skip signal to the after-hook, so every skipped scenario
 * was recorded twice, once correctly and once as passed.
 *
 * <p>Since the results are now a Cucumber Messages stream rather than a format this project defines,
 * the rule is a property of what Cucumber emits, and only a real Cucumber run can demonstrate it.
 * These tests therefore execute a fixture suite through the JUnit Platform — same engine, same gate,
 * same plugin — and read the stream back as a consumer would: outcome per scenario derived from the
 * most severe step result, exactly as the {@code cucumber-query} helpers do it.
 *
 * <p>What is <em>not</em> asserted here is the serialisation of the stream. That is Cucumber's own
 * {@code MessageFormatter}, and re-checking it would only be checking Cucumber.
 */
class ConformanceReportPluginTest {

    /** The fixture's {@code @stale} tag is deliberately absent, so two scenarios must be skipped. */
    private static final Set<Capability> DECLARED = EnumSet.of(Capability.OBJECT, Capability.EVENTS);

    private static final String CONFIGURATION = "my-provider-rpc";

    private static final String FEATURE_URI = "classpath:report-selftest/report.feature";

    private static final String OUTLINE = "Requesting the wrong type returns the code default";

    /**
     * Severity order of {@code TestStepResultStatus}, least to most severe.
     *
     * <p>A {@code testCaseFinished} message carries no status: a scenario's outcome is the most
     * severe result among its steps, hooks included. That is not incidental — it is the mechanism
     * that makes a gated skip truthful, because the aborted {@code @Before} hook contributes a
     * {@code SKIPPED} result that outranks every {@code PASSED} step it prevented from running.
     */
    private static final List<String> SEVERITY =
            Arrays.asList("UNKNOWN", "PASSED", "SKIPPED", "PENDING", "UNDEFINED", "AMBIGUOUS", "FAILED");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    static Path reportDir;

    private static JsonNode envelope;
    private static byte[] resultsBytes;
    private static Results results;

    @BeforeAll
    static void runTheFixtureSuite() throws IOException {
        runSuite(reportDir, DECLARED);

        Path envelopePath = reportDir.resolve(CONFIGURATION + ".json");
        Path resultsPath = reportDir.resolve(CONFIGURATION + ".ndjson");
        assertThat(envelopePath).exists();
        assertThat(resultsPath).exists();

        envelope = MAPPER.readTree(Files.readAllBytes(envelopePath));
        resultsBytes = Files.readAllBytes(resultsPath);
        results = Results.parse(new String(resultsBytes, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("every scenario appears exactly once, whatever happened to it")
    void everyScenarioAppearsExactlyOnce() {
        // Seven pickles: four plain scenarios and three outline rows. A report that quietly omitted
        // the ones it did not run would satisfy every other rule here and still mislead, because a
        // reader would have no way to know how many questions went unasked.
        assertThat(results.pickles).hasSize(7);

        assertThat(results.pickleOfTestCase.values())
                .as("one test case per pickle, and no pickle executed twice")
                .containsExactlyInAnyOrderElementsOf(results.pickles.keySet());

        assertThat(results.testCaseOfStarted.values())
                .as("one execution per test case; a scenario counted twice is how Go's report went wrong")
                .containsExactlyInAnyOrderElementsOf(results.pickleOfTestCase.keySet());

        assertThat(results.finished)
                .as("every started scenario also finished")
                .containsExactlyInAnyOrderElementsOf(results.testCaseOfStarted.keySet());
    }

    @Test
    @DisplayName("the outcome counts are what the fixture describes")
    void theOutcomeCountsAreWhatTheFixtureDescribes() {
        assertThat(results.outcomeCounts())
                .containsExactlyInAnyOrderEntriesOf(counts("PASSED", 4L, "FAILED", 1L, "SKIPPED", 2L));
    }

    @Test
    @DisplayName("a scenario gated on an undeclared capability is skipped, never passed")
    void aGatedScenarioIsSkipped() {
        List<String> gated = new ArrayList<>();
        for (Map.Entry<String, JsonNode> pickle : results.pickles.entrySet()) {
            if (needsSomethingUndeclared(results.tagsOf(pickle.getValue()))) {
                gated.add(pickle.getKey());
            }
        }

        assertThat(gated)
                .as("the premise: the fixture has a gated plain scenario and a gated outline row")
                .hasSize(2);

        for (String pickleId : gated) {
            assertThat(results.outcomeOfPickle(pickleId))
                    .as("pickle %s carries an undeclared capability tag", pickleId)
                    .isEqualTo("SKIPPED");
        }

        assertThat(results.outcomeCounts().get("SKIPPED"))
                .as("nothing else was skipped, so a skip cannot hide an unrelated one")
                .isEqualTo((long) gated.size());
    }

    @Test
    @DisplayName("the gate's own reason survives into the stream")
    void theGateReasonSurvives() {
        // The declaration in the envelope is enough to work out *why* a scenario was skipped, but
        // the reason the gate gave is carried too, on the hook result that produced the skip.
        assertThat(results.messagesOfSkippedSteps()).isNotEmpty().allSatisfy(message -> assertThat(message)
                .contains("does not declare capability")
                .contains("STALE"));
    }

    @Test
    @DisplayName("a scenario's tags include one set on its Examples block alone")
    void tagsIncludeExamplesBlockTags() {
        List<JsonNode> rows = results.picklesNamed(OUTLINE);

        assertThat(rows).hasSize(3);
        assertThat(rows).as("the feature tag reaches every row").allSatisfy(row -> assertThat(results.tagsOf(row))
                .contains("@events"));

        // Gherkin allows a tag on an individual Examples block, so two rows of one outline can
        // differ in whether the gate stops them. Both rows must appear, and only the tagged one
        // may be skipped: a skip that took its siblings with it would be invisible in the totals.
        List<String> outcomes = new ArrayList<>();
        List<Boolean> stale = new ArrayList<>();
        for (JsonNode row : rows) {
            outcomes.add(results.outcomeOfPickle(row.get("id").asText()));
            stale.add(results.tagsOf(row).contains("@stale"));
        }
        assertThat(stale).containsExactly(false, false, true);
        assertThat(outcomes).containsExactly("PASSED", "PASSED", "SKIPPED");
    }

    @Test
    @DisplayName("each Scenario Outline row is identified by the Examples row it came from")
    void outlineRowsAreDistinguishable() {
        List<JsonNode> rows = results.picklesNamed(OUTLINE);

        assertThat(rows)
                .as("all three rows share one name, which is the premise of this test")
                .extracting(row -> row.get("name").asText())
                .containsOnly(OUTLINE);

        // A pickle's astNodeIds are [scenario, table row] for an outline-derived scenario. The
        // second is the row's identity, and it resolves in the gherkinDocument to the cells the row
        // was compiled from. This is the mechanism the TCK used to reverse-engineer from a pickle's
        // reported line number; the stream states it outright.
        List<String> rowIds = new ArrayList<>();
        List<List<String>> cells = new ArrayList<>();
        for (JsonNode row : rows) {
            JsonNode astNodeIds = row.get("astNodeIds");
            assertThat(astNodeIds).hasSize(2);
            String rowId = astNodeIds.get(1).asText();
            rowIds.add(rowId);
            cells.add(results.cellsOfTableRow(rowId));
        }

        assertThat(rowIds).doesNotHaveDuplicates();
        assertThat(cells)
                .containsExactly(
                        Arrays.asList("string-flag", "Boolean"),
                        Arrays.asList("string-flag", "Integer"),
                        Arrays.asList("boolean-flag", "String"));
    }

    @Test
    @DisplayName("the stream carries the source of the feature that executed")
    void theStreamCarriesTheExecutedSource() throws IOException {
        assertThat(results.sources).containsOnlyKeys(FEATURE_URI);

        // Line endings are normalised on both sides: this repo is checked out with whatever the
        // platform does, and the point of the assertion is that the text is the file's, not that
        // Cucumber preserves CRLF.
        byte[] onDisk = Files.readAllBytes(Paths.get("src/test/resources/report-selftest/report.feature"));
        assertThat(results.sources.get(FEATURE_URI).replace("\r\n", "\n"))
                .as("what ran, verbatim, rather than a claim about which revision it came from")
                .isEqualTo(new String(onDisk, StandardCharsets.UTF_8).replace("\r\n", "\n"));
    }

    @Test
    @DisplayName("the envelope points at the results and covers them with a digest")
    void theEnvelopePointsAtTheResults() throws NoSuchAlgorithmException {
        JsonNode reference = envelope.get("results");

        assertThat(reference.get("format").asText()).isEqualTo("cucumber-messages");
        assertThat(reference.get("location").asText())
                .as("a path relative to the envelope, so a published pair can be moved together")
                .isEqualTo(CONFIGURATION + ".ndjson");
        assertThat(reference.get("digest").asText()).isEqualTo(sha256Of(resultsBytes));
    }

    @Test
    @DisplayName("the envelope carries everything the schema requires and nothing it forbids")
    void theEnvelopeCarriesWhatTheSchemaRequires() {
        assertThat(envelope.get("schemaVersion").asText()).isEqualTo("1");
        assertThat(envelope.get("provider").get("name").asText()).isEqualTo("My Provider");
        assertThat(envelope.get("provider").get("language").asText()).isEqualTo("java");
        assertThat(envelope.get("provider").get("configuration").asText()).isEqualTo(CONFIGURATION);
        assertThat(envelope.get("sdk").get("name").asText()).isEqualTo("dev.openfeature:sdk");
        assertThat(envelope.get("sdk").get("version").asText()).isNotEmpty();
        assertThat(envelope.get("tck").get("implementation").asText()).isEqualTo("java-sdk-contrib/tools/provider-tck");
        assertThat(envelope.get("tck").get("specRevision").asText()).hasSizeGreaterThanOrEqualTo(7);
        assertThat(envelope.get("backend").get("controlApi").asText()).isEqualTo("http");

        // The schema sets additionalProperties: false throughout, so anything the results payload
        // now owns is a validation failure rather than harmless duplication.
        List<String> fields = new ArrayList<>();
        envelope.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).doesNotContain("scenarios", "capabilities");
        assertThat(envelope.get("tck").has("assetsTree")).isFalse();
    }

    @Test
    @DisplayName("the declaration lists the capabilities the provider claims, in vocabulary order")
    void theDeclarationListsWhatIsClaimed() {
        JsonNode declared = envelope.get("declaration").get("declared");

        // The declaration is an input to reading the results, not a summary of them: the stream says
        // a scenario was skipped, and only this says whether the provider declines what it needed.
        assertThat(declared)
                .extracting(JsonNode::asText)
                .containsExactly(Capability.EVENTS.tag(), Capability.OBJECT.tag());
    }

    @Test
    @DisplayName("a reserved capability cannot reach the declaration, even when everything is claimed")
    void aReservedCapabilityCannotReachTheDeclaration() {
        // The provider that claims the most is the case that used to break the rule: "everything"
        // spelt EnumSet.allOf, or "everything except X" spelt EnumSet.complementOf, collected the
        // reserved tags along the way and published a claim about two capabilities no scenario
        // examines. So this asks the maximal declaration for its report.
        JsonNode declared = MAPPER.valueToTree(report(Capability.declarable()))
                .get("declaration")
                .get("declared");

        List<String> tags = new ArrayList<>();
        declared.forEach(tag -> tags.add(tag.asText()));

        assertThat(tags)
                .as("a reserved tag gates nothing, so declaring it is a claim nothing can contradict")
                .doesNotContain(Capability.TARGETING.tag(), Capability.CACHING.tag());
        assertThat(tags)
                .as("and every capability some scenario does gate is still there")
                .containsExactly(
                        Capability.LIFECYCLE.tag(),
                        Capability.REINITIALIZATION.tag(),
                        Capability.EVENTS.tag(),
                        Capability.STALE.tag(),
                        Capability.CONFIGURATION_CHANGE.tag(),
                        Capability.OBJECT.tag(),
                        Capability.UNAVAILABLE_INIT.tag(),
                        Capability.NUMERIC_COERCION.tag(),
                        // @large-integers gates a scenario and is an ordinary declarable capability.
                        // No Java provider holds it, because the SDK's integer accessor is 32 bits,
                        // but that is a fact about the SDK recorded in Appendix F rather than a
                        // second kind of declaration, so the maximal claim includes it and a real
                        // harness withholds it.
                        Capability.LARGE_INTEGERS.tag());
    }

    @Test
    @DisplayName("naming a reserved capability fails the run rather than being dropped quietly")
    void namingAReservedCapabilityFailsTheRun() {
        // Chosen over a warning: the declaration is the one part of the report no result can check,
        // and a report is read long after the log it would have been warned in has gone. Nothing is
        // lost by refusing, because no scenario carries the tag.
        Set<Capability> overclaimed = EnumSet.of(Capability.OBJECT, Capability.TARGETING);

        assertThatThrownBy(() -> metadata(overclaimed))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(Capability.TARGETING.tag())
                .hasMessageContaining("declarableExcept");
    }

    @Test
    @DisplayName("\"everything except X\" means everything declarable except X")
    void declarableExceptYieldsOnlyDeclarableCapabilities() {
        assertThat(Capability.declarableExcept(Capability.STALE))
                .doesNotContain(Capability.STALE, Capability.TARGETING, Capability.CACHING)
                .contains(Capability.OBJECT, Capability.NUMERIC_COERCION);

        // The counterpart it replaces, and why it had to be replaced.
        assertThat(EnumSet.complementOf(EnumSet.of(Capability.STALE)))
                .as("complementOf is the complement of the enum, not of the declarable vocabulary")
                .contains(Capability.TARGETING, Capability.CACHING);
    }

    @Test
    @DisplayName("a withheld capability that is a defect is reported as a deviation")
    void aDefectIsReportedAsADeviation() {
        JsonNode deviations = envelope.get("knownDeviations");

        assertThat(deviations).hasSize(1);
        assertThat(deviations.get(0).get("capability").asText()).isEqualTo(Capability.NUMERIC_COERCION.tag());
        assertThat(deviations.get(0).get("summary").asText()).isNotEmpty();
        assertThat(deviations.get(0).has("issue"))
                .as("the fixture's deviation is untracked, and the field is omitted rather than empty")
                .isFalse();
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
    @DisplayName("nothing is collected or written when no report directory is configured")
    void nothingIsWrittenByDefault(@TempDir Path dir) throws IOException {
        CountingEventPublisher publisher = new CountingEventPublisher();
        new ConformanceReportPlugin(() -> null, () -> Optional.of(metadata(DECLARED))).setEventPublisher(publisher);

        assertThat(publisher.registered)
                .as("not even the message formatter is wired up, so an unasked-for run buffers nothing")
                .isZero();
        try (Stream<Path> written = Files.list(dir)) {
            assertThat(written).isEmpty();
        }
    }

    @Test
    @DisplayName("both files are named after the configuration, safely")
    void theFilesAreNamedAfterTheConfiguration() {
        assertThat(ReportNames.configurationOf(MyProviderRpcTckTest.class)).isEqualTo(CONFIGURATION);
        assertThat(ReportNames.baseNameOf("flagd-rpc")).isEqualTo("flagd-rpc");
        assertThat(ReportNames.baseNameOf("flagd/rpc"))
                .as("a configuration name is chosen to read well, not to be path-safe")
                .isEqualTo("flagd-rpc");
        assertThat(ReportNames.baseNameOf("../escape")).isEqualTo("escape");
    }

    /** A suite whose name the default configuration derivation has to cope with. */
    private static final class MyProviderRpcTckTest {}

    /**
     * Runs the fixture feature through the real Cucumber engine, with the real plugin registered.
     *
     * <p>Driven through the JUnit Platform rather than through Cucumber's CLI because that is how the
     * TCK itself runs: {@link ProviderTckTest} is a JUnit Platform Suite, and the plugin's
     * position in the event stream is a property of that engine.
     */
    private static void runSuite(Path dir, Set<Capability> declared) {
        ReportSelfTestSteps.declare(declared);
        TckRuntime.recordRun(metadata(declared));
        String previous = System.getProperty(ConformanceReportPlugin.REPORT_DIR_PROPERTY);
        System.setProperty(ConformanceReportPlugin.REPORT_DIR_PROPERTY, dir.toString());
        try {
            LauncherFactory.create()
                    .execute(LauncherDiscoveryRequestBuilder.request()
                            .selectors(DiscoverySelectors.selectClasspathResource("report-selftest"))
                            .filters(EngineFilter.includeEngines("cucumber"))
                            .configurationParameter(
                                    Constants.GLUE_PROPERTY_NAME,
                                    ReportSelfTestSteps.class.getPackage().getName())
                            .configurationParameter(
                                    Constants.PLUGIN_PROPERTY_NAME, ConformanceReportPlugin.class.getName())
                            .configurationParameter(Constants.PARALLEL_EXECUTION_ENABLED_PROPERTY_NAME, "false")
                            .configurationParameter(
                                    Constants.OBJECT_FACTORY_PROPERTY_NAME, "io.cucumber.picocontainer.PicoFactory")
                            .build());
        } finally {
            if (previous == null) {
                System.clearProperty(ConformanceReportPlugin.REPORT_DIR_PROPERTY);
            } else {
                System.setProperty(ConformanceReportPlugin.REPORT_DIR_PROPERTY, previous);
            }
            TckRuntime.recordRun(null);
        }
    }

    /** Builds the envelope a run with this declaration would emit, without running one. */
    private static ConformanceReport report(Set<Capability> declared) {
        return new ConformanceReportPlugin(() -> null, Optional::empty)
                .build(metadata(declared), CONFIGURATION + ".ndjson", "sha256:0", "26.1.0");
    }

    private static TckRunMetadata metadata(Set<Capability> declared) {
        TckRunMetadata metadata = new TckRunMetadata(
                CONFIGURATION,
                declared,
                "a test double",
                "http",
                Collections.singletonList(KnownDeviation.untracked(
                        Capability.NUMERIC_COERCION, "the fixture provider narrows a float to an integer")));
        metadata.recordProviderName("My Provider");
        return metadata;
    }

    private static boolean needsSomethingUndeclared(List<String> tags) {
        for (String tag : tags) {
            Optional<Capability> capability = Capability.fromTag(tag);
            if (capability.isPresent() && !DECLARED.contains(capability.get())) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Long> counts(Object... statusesAndCounts) {
        Map<String, Long> expected = new LinkedHashMap<>();
        for (int i = 0; i < statusesAndCounts.length; i += 2) {
            expected.put((String) statusesAndCounts[i], (Long) statusesAndCounts[i + 1]);
        }
        return expected;
    }

    private static String sha256Of(byte[] bytes) throws NoSuchAlgorithmException {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder hex = new StringBuilder("sha256:");
        for (byte b : digest) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    /** Counts what a plugin registers, for the case where it should register nothing. */
    private static final class CountingEventPublisher implements EventPublisher {

        private int registered;

        @Override
        public <T> void registerHandlerFor(Class<T> eventType, EventHandler<T> handler) {
            registered++;
        }

        @Override
        public <T> void removeHandlerFor(Class<T> eventType, EventHandler<T> handler) {
            registered--;
        }
    }

    /**
     * A Cucumber Messages stream, read the way a consumer reads one.
     *
     * <p>Deliberately built from the raw ndjson with nothing but Jackson. Using Cucumber's own query
     * helpers would make these tests agree with Cucumber by construction; the point is to show that
     * the facts the report needs are recoverable from the bytes on disk.
     */
    private static final class Results {

        private final Map<String, JsonNode> pickles = new LinkedHashMap<>();
        private final Map<String, String> pickleOfTestCase = new LinkedHashMap<>();
        private final Map<String, String> testCaseOfStarted = new LinkedHashMap<>();
        private final Map<String, String> mostSevereByStarted = new LinkedHashMap<>();
        private final Map<String, JsonNode> tableRows = new LinkedHashMap<>();
        private final Map<String, String> sources = new TreeMap<>();
        private final List<String> finished = new ArrayList<>();
        private final List<String> skippedStepMessages = new ArrayList<>();

        static Results parse(String ndjson) throws IOException {
            Results results = new Results();
            for (String line : ndjson.split("\n")) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                results.accept(MAPPER.readTree(line));
            }
            return results;
        }

        private void accept(JsonNode message) {
            if (message.has("source")) {
                JsonNode source = message.get("source");
                sources.put(source.get("uri").asText(), source.get("data").asText());
            }
            if (message.has("gherkinDocument")) {
                collectTableRows(message.get("gherkinDocument"));
            }
            if (message.has("pickle")) {
                JsonNode pickle = message.get("pickle");
                pickles.put(pickle.get("id").asText(), pickle);
            }
            if (message.has("testCase")) {
                JsonNode testCase = message.get("testCase");
                pickleOfTestCase.put(
                        testCase.get("id").asText(), testCase.get("pickleId").asText());
            }
            if (message.has("testCaseStarted")) {
                JsonNode started = message.get("testCaseStarted");
                testCaseOfStarted.put(
                        started.get("id").asText(), started.get("testCaseId").asText());
            }
            if (message.has("testCaseFinished")) {
                finished.add(
                        message.get("testCaseFinished").get("testCaseStartedId").asText());
            }
            if (message.has("testStepFinished")) {
                JsonNode step = message.get("testStepFinished");
                String startedId = step.get("testCaseStartedId").asText();
                JsonNode result = step.get("testStepResult");
                String status = result.get("status").asText();
                mostSevereByStarted.merge(startedId, status, Results::moreSevere);
                if ("SKIPPED".equals(status) && result.has("message")) {
                    skippedStepMessages.add(result.get("message").asText());
                }
            }
        }

        private void collectTableRows(JsonNode document) {
            for (JsonNode child : orEmpty(document.get("feature"), "children")) {
                JsonNode scenario = child.get("scenario");
                if (scenario == null) {
                    continue;
                }
                for (JsonNode examples : orEmpty(scenario, "examples")) {
                    for (JsonNode row : orEmpty(examples, "tableBody")) {
                        tableRows.put(row.get("id").asText(), row);
                    }
                }
            }
        }

        private static Iterable<JsonNode> orEmpty(JsonNode parent, String field) {
            JsonNode node = parent == null ? null : parent.get(field);
            return node == null ? Collections.emptyList() : node;
        }

        private static String moreSevere(String left, String right) {
            return SEVERITY.indexOf(left) >= SEVERITY.indexOf(right) ? left : right;
        }

        Map<String, Long> outcomeCounts() {
            Map<String, Long> counts = new LinkedHashMap<>();
            for (String status : mostSevereByStarted.values()) {
                counts.merge(status, 1L, Long::sum);
            }
            return counts;
        }

        String outcomeOfPickle(String pickleId) {
            for (Map.Entry<String, String> started : testCaseOfStarted.entrySet()) {
                if (pickleId.equals(pickleOfTestCase.get(started.getValue()))) {
                    return mostSevereByStarted.get(started.getKey());
                }
            }
            throw new AssertionError("pickle " + pickleId + " was never executed");
        }

        List<String> tagsOf(JsonNode pickle) {
            List<String> tags = new ArrayList<>();
            for (JsonNode tag : orEmpty(pickle, "tags")) {
                tags.add(tag.get("name").asText());
            }
            return tags;
        }

        List<JsonNode> picklesNamed(String name) {
            List<JsonNode> matching = new ArrayList<>();
            for (JsonNode pickle : pickles.values()) {
                if (name.equals(pickle.get("name").asText())) {
                    matching.add(pickle);
                }
            }
            return matching;
        }

        List<String> cellsOfTableRow(String rowId) {
            JsonNode row = tableRows.get(rowId);
            assertThat(row)
                    .as("ast node %s is a table row in the gherkin document", rowId)
                    .isNotNull();
            List<String> values = new ArrayList<>();
            for (JsonNode cell : orEmpty(row, "cells")) {
                values.add(cell.get("value").asText());
            }
            return values;
        }

        List<String> messagesOfSkippedSteps() {
            return skippedStepMessages;
        }
    }
}
