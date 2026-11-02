package dev.openfeature.contrib.tools.tck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.openfeature.contrib.tools.tck.selftest.ReportSelfTestSteps;
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
 * Checks the rule the conformance report exists for: a scenario skipped for an undeclared capability
 * is reported as skipped with the reason, never as passed. That is a property of what Cucumber emits,
 * so a fixture suite runs through the JUnit Platform with the real plugin and the stream is read back
 * as a consumer would. Serialisation of the stream is Cucumber's own and is not asserted.
 */
class ConformanceReportPluginTest {

    /**
     * {@code @stale} is deliberately undeclared, so two scenarios are skipped, plus a third that no
     * declaration could cover because {@code @large-integers} is
     * {@linkplain Capability#inexpressible() inexpressible} in Java.
     */
    private static final Set<Capability> DECLARED = EnumSet.of(Capability.OBJECT, Capability.EVENTS);

    private static final String CONFIGURATION = "my-provider-rpc";

    private static final String FEATURE_URI = "classpath:report-selftest/report.feature";

    private static final String OUTLINE = "Requesting the wrong type returns the code default";

    /**
     * Severity order of {@code TestStepResultStatus}, least to most severe. A scenario's outcome is
     * its most severe step result, hooks included, which is what makes a gated skip truthful.
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
        // Eight pickles: five plain scenarios and three outline rows, none of them omitted.
        assertThat(results.pickles).hasSize(8);

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
                .containsExactlyInAnyOrderEntriesOf(counts("PASSED", 4L, "FAILED", 1L, "SKIPPED", 3L));
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
                .as("the premise: the fixture has a gated plain scenario, a gated outline row and an "
                        + "inexpressible one, which no declaration could have contained")
                .hasSize(3);

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
    @DisplayName("the gate's own reason survives into the stream, and the two kinds of skip stay apart")
    void theGateReasonSurvives() {
        // The reason the gate gave is carried on the hook result that produced the skip.
        List<String> messages = results.messagesOfSkippedSteps();
        assertThat(messages).hasSize(3);

        // @large-integers is absent from every Java declaration because the SDK's integer accessor
        // is 32 bits, not because the provider declined, so the message must name the SDK.
        List<String> unaskable = new ArrayList<>();
        messages.forEach(message -> {
            if (message.contains("LARGE_INTEGERS")) {
                unaskable.add(message);
            }
        });
        assertThat(unaskable).hasSize(1);
        assertThat(unaskable.get(0))
                .contains("the Java SDK cannot express capability")
                .contains("not the provider under test declining")
                .doesNotContain("does not declare capability");

        assertThat(messages)
                .filteredOn(message -> !message.contains("LARGE_INTEGERS"))
                .hasSize(2)
                .allSatisfy(message -> assertThat(message)
                        .contains("does not declare capability")
                        .contains("STALE"));
    }

    @Test
    @DisplayName("a scenario's tags include one set on its Examples block alone")
    void tagsIncludeExamplesBlockTags() {
        List<JsonNode> rows = results.picklesNamed(OUTLINE);

        assertThat(rows).hasSize(3);
        assertThat(rows)
                .as("the feature tag reaches every row")
                .allSatisfy(row -> assertThat(results.tagsOf(row)).contains("@events"));

        // Gherkin allows a tag on a single Examples block, so only the tagged row may be skipped.
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

        // A pickle's astNodeIds are [scenario, table row] for an outline row; the second resolves in
        // the gherkinDocument to the cells the row was compiled from.
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

        // Line endings are normalised: the text must be the file's, whatever the checkout did to CRLF.
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
        assertThat(envelope.get("tck").get("implementation").asText()).isEqualTo("java-sdk-contrib/tools/tck");
        assertThat(envelope.get("tck").get("specRevision").asText()).hasSizeGreaterThanOrEqualTo(7);
        assertThat(envelope.get("tck").get("specRevision").asText())
                .as("and it is the revision the packaged assets actually came from; see the test below")
                .isEqualTo(CanonicalAssetDigestTest.PINNED_REVISION);
        // Both are required by the schema, so asserted as present rather than present-if-set.
        assertThat(envelope.has("backend")).isTrue();
        assertThat(envelope.get("backend").get("backendType").asText()).isEqualTo("http");

        // The schema sets additionalProperties: false, so anything the results payload owns would fail.
        List<String> fields = new ArrayList<>();
        envelope.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).doesNotContain("scenarios", "capabilities");
        assertThat(envelope.get("tck").has("assetsTree")).isFalse();
    }

    @Test
    @DisplayName("the declaration lists the capabilities the provider claims, in vocabulary order")
    void theDeclarationListsWhatIsClaimed() {
        JsonNode declared = envelope.get("declaration").get("declared");

        // Not derivable from the results: a skip does not say whether the provider declined the capability.
        assertThat(declared)
                .extracting(JsonNode::asText)
                .containsExactly(Capability.EVENTS.tag(), Capability.OBJECT.tag());
    }

    @Test
    @DisplayName("a reserved capability cannot reach the declaration, even when everything is claimed")
    void aReservedCapabilityCannotReachTheDeclaration() {
        // EnumSet.allOf and complementOf used to collect the reserved tags and publish a claim no
        // scenario examines, so this asks the maximal declaration for its report.
        JsonNode declared = MAPPER.valueToTree(report(Capability.declarable()))
                .get("declaration")
                .get("declared");

        List<String> tags = new ArrayList<>();
        declared.forEach(tag -> tags.add(tag.asText()));

        assertThat(tags)
                .as("a reserved tag gates nothing, so declaring it is a claim nothing can contradict")
                .doesNotContain(Capability.CACHING.tag());
        assertThat(tags)
                .as("and every capability some scenario does gate is still there")
                .containsExactly(
                        Capability.LIFECYCLE.tag(),
                        Capability.REINITIALIZATION.tag(),
                        Capability.EVENTS.tag(),
                        Capability.STALE.tag(),
                        Capability.CONFIGURATION_CHANGE.tag(),
                        Capability.OBJECT.tag(),
                        Capability.VARIANTS.tag(),
                        Capability.DISABLED_FLAGS.tag(),
                        Capability.UNAVAILABLE_INIT.tag(),
                        Capability.NUMERIC_COERCION.tag(),
                        Capability.STRING_TYPING.tag(),
                        Capability.FULLY_TYPED_VALUES.tag(),
                        // @large-integers is absent: the Java SDK's integer accessor is 32 bits, so no Java
                        // provider may claim it.
                        Capability.TARGETING.tag(),
                        Capability.STANDARD_REASONS.tag());
    }

    @Test
    @DisplayName("the revision a report publishes is the one the packaged assets came from")
    void theReportedRevisionIsTheRevisionTheAssetsCameFrom() {
        // The revision a report publishes is a POM property kept by hand beside the submodule gitlink.
        // This ties what the build filtered into the JAR to PINNED_REVISION, which
        // CanonicalAssetDigestTest has already tied to the packaged assets.
        assertThat(TckBuildInfo.specRevision())
                .as("tck.spec.revision in tools/tck/pom.xml must equal "
                        + "CanonicalAssetDigestTest.PINNED_REVISION -- a re-pin moves the submodule "
                        + "gitlink, that constant and this property together")
                .isEqualTo(CanonicalAssetDigestTest.PINNED_REVISION);
        assertThat(TckBuildInfo.specRevision())
                .as("and the properties resource must actually have been filtered, or both are 'unknown'")
                .isNotEqualTo(TckBuildInfo.UNKNOWN);
    }

    @Test
    @DisplayName("naming a reserved capability fails the run rather than being dropped quietly")
    void namingAReservedCapabilityFailsTheRun() {
        // Refused rather than warned: the declaration is the one part of the report no result can check.
        Set<Capability> overclaimed = EnumSet.of(Capability.OBJECT, Capability.CACHING);

        assertThatThrownBy(() -> metadata(overclaimed))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(Capability.CACHING.tag())
                .hasMessageContaining("Capability.declarable()");
    }

    @Test
    @DisplayName("\"everything except X\" means everything declarable except X")
    void declarableExceptYieldsOnlyDeclarableCapabilities() {
        assertThat(Capability.declarableExcept(Capability.STALE))
                .doesNotContain(Capability.STALE, Capability.CACHING)
                .contains(Capability.OBJECT, Capability.NUMERIC_COERCION, Capability.TARGETING);

        assertThat(EnumSet.complementOf(EnumSet.of(Capability.STALE)))
                .as("complementOf is the complement of the enum, not of the declarable vocabulary")
                .contains(Capability.CACHING);
    }

    @Test
    @DisplayName("a withheld capability that is a defect is reported as a deviation")
    void aDefectIsReportedAsADeviation() {
        JsonNode deviations = envelope.get("knownDeviations");

        assertThat(deviations).hasSize(1);
        assertThat(deviations.get(0).get("capability").asText()).isEqualTo(Capability.CONFIGURATION_CHANGE.tag());
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
    @DisplayName("the report-directory knob is spelled the way the other three languages spell it")
    void theReportDirectoryKnobIsSpelledPortably() {
        // Literals on purpose: a cross-language CI job exports one variable to drive all four TCKs,
        // so a rename is a breaking change no compiler checks.
        assertThat(ConformanceReportPlugin.REPORT_DIR_ENV).isEqualTo("TCK_REPORT_DIR");
        assertThat(ConformanceReportPlugin.REPORT_DIR_PROPERTY).isEqualTo("tck.report.dir");
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

    /** Runs the fixture feature through the real Cucumber engine with the real plugin registered. */
    private static void runSuite(Path dir, Set<Capability> declared) {
        ReportSelfTestSteps.declare(declared);
        TckRunMetadata.record(metadata(declared));
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
            TckRunMetadata.record(null);
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
                BackendType.HTTP,
                // Withheld-and-skipped: the shape a fixture may depict, as opposed to a deviation, which is
                // for a provider that attempts a behaviour and gets it wrong.
                Collections.singletonList(KnownDeviation.untracked(
                        Capability.CONFIGURATION_CHANGE,
                        "the fixture provider swallows its child's change events and can never " + "report one")));
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
     * A Cucumber Messages stream read from the raw ndjson with Jackson alone, so the tests do not
     * agree with Cucumber's query helpers by construction.
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
