package dev.openfeature.contrib.tools.providertck;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.cucumber.plugin.ConcurrentEventListener;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.Result;
import io.cucumber.plugin.event.Status;
import io.cucumber.plugin.event.TestCase;
import io.cucumber.plugin.event.TestCaseFinished;
import io.cucumber.plugin.event.TestRunFinished;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes a machine-readable conformance report for the suite that just ran.
 *
 * <p>Registered automatically by {@link AbstractProviderTckTest}, so an adopter changes nothing to
 * get one. It is opt-in per <em>run</em>: set {@value #REPORT_DIR_ENV} (or the
 * {@value #REPORT_DIR_PROPERTY} system property) and each suite writes
 * {@code <dir>/<configuration>.json}. Emitting a report is a property of the run rather than of the
 * code — CI asks for one, a developer running the suite locally does not — and unset means no
 * report, which is not an error. Several suites in one JVM each write their own file, so flagd's two
 * resolvers do not collide.
 *
 * <p><strong>Why the per-scenario list is the load-bearing part.</strong> Appendix F requires that a
 * scenario skipped for an undeclared capability is reported as skipped with the reason and never as
 * passed. This plugin makes that checkable by a consumer rather than dependent on the runner's
 * summary being trustworthy: it records the outcome of every scenario, exactly once, straight from
 * Cucumber's own {@code TestCaseFinished} event. One event in, one entry out — there is no path by
 * which a skip is also counted as a pass, which is precisely the bug the Go TCK had to fix, where
 * the capability-skip signal did not reach the after-hook and every skipped scenario was recorded
 * twice.
 *
 * @see <a href="https://github.com/open-feature/spec/issues/424">open-feature/spec#424</a>
 */
public final class ConformanceReportPlugin implements ConcurrentEventListener {

    /** Environment variable naming the directory reports are written to. */
    public static final String REPORT_DIR_ENV = "PROVIDER_TCK_REPORT_DIR";

    /**
     * System property naming the directory reports are written to, taking precedence over the
     * environment variable.
     *
     * <p>Accepted in addition to {@value #REPORT_DIR_ENV} because {@code -D} is how a Maven or
     * Gradle invocation is usually parameterised. The environment variable is the portable spelling
     * and is what every other language's TCK reads, so a cross-language CI job can set one thing.
     */
    public static final String REPORT_DIR_PROPERTY = "provider.tck.report.dir";

    private static final Logger log = LoggerFactory.getLogger(ConformanceReportPlugin.class);

    private static final String LANGUAGE = "java";

    private final List<ScenarioRecord> records = Collections.synchronizedList(new ArrayList<>());
    private final Supplier<String> reportDir;
    private final Supplier<Optional<TckRunMetadata>> metadata;

    /** Creates the plugin Cucumber instantiates by name. */
    public ConformanceReportPlugin() {
        this(ConformanceReportPlugin::configuredReportDir, TckRuntime::lastRunMetadata);
    }

    ConformanceReportPlugin(Supplier<String> reportDir, Supplier<Optional<TckRunMetadata>> metadata) {
        this.reportDir = reportDir;
        this.metadata = metadata;
    }

    @Override
    public void setEventPublisher(EventPublisher publisher) {
        publisher.registerHandlerFor(TestCaseFinished.class, this::onTestCaseFinished);
        // The end-of-run event carries the run's own result, which the report has no use for: what
        // matters is the outcome of each scenario, which TestCaseFinished has already delivered.
        publisher.registerHandlerFor(TestRunFinished.class, finished -> writeReport());
    }

    /**
     * Returns the directory reports are written to, or {@code null} when none was configured.
     *
     * @return the configured report directory, trimmed, or {@code null}
     */
    static String configuredReportDir() {
        String property = System.getProperty(REPORT_DIR_PROPERTY);
        if (property != null && !property.trim().isEmpty()) {
            return property.trim();
        }
        String environment = System.getenv(REPORT_DIR_ENV);
        if (environment != null && !environment.trim().isEmpty()) {
            return environment.trim();
        }
        return null;
    }

    private void onTestCaseFinished(TestCaseFinished event) {
        TestCase testCase = event.getTestCase();
        Result result = event.getResult();
        records.add(new ScenarioRecord(
                featureName(testCase),
                testCase.getName(),
                Collections.unmodifiableList(new ArrayList<>(testCase.getTags())),
                result.getStatus(),
                messageOf(result),
                result.getDuration().toNanos() / 1_000_000.0));
    }

    private void writeReport() {
        String dir = reportDir.get();
        if (dir == null) {
            return;
        }

        Optional<TckRunMetadata> run = metadata.get();
        if (!run.isPresent()) {
            // Nothing observed the suite, which means it never got as far as starting the runtime.
            // The run has failed for some other reason by now; adding a report with an invented
            // provider name on top of that would only mislead.
            log.error(
                    "{} is set but no TCK suite ran, so there is nothing to report on. "
                            + "This normally means the suite failed before its backend stack started.",
                    REPORT_DIR_ENV);
            return;
        }

        write(build(run.get()), dir, run.get().configuration());
    }

    /**
     * Assembles the report from what the run observed.
     *
     * @param run what the runtime recorded about this suite
     * @return the report, ready to serialise
     */
    ConformanceReport build(TckRunMetadata run) {
        List<ScenarioRecord> observed;
        synchronized (records) {
            observed = new ArrayList<>(records);
        }
        observed.sort(Comparator.comparing((ScenarioRecord r) -> r.feature).thenComparing(r -> r.name));

        Set<Capability> declared = run.capabilities();
        List<ConformanceReport.ScenarioResult> scenarios = new ArrayList<>(observed.size());

        // Only a capability that something actually gated on can be said to have failed, so this
        // counts the failures as the scenarios are converted rather than guessing afterwards. The
        // count goes into the capability's reason, which the schema requires for anything that did
        // not pass.
        Map<Capability, Integer> failed = new EnumMap<>(Capability.class);

        for (ScenarioRecord record : observed) {
            scenarios.add(resolve(record, declared, failed));
        }

        return new ConformanceReport(
                new ConformanceReport.Provider(run.providerName(), LANGUAGE, run.configuration()),
                new ConformanceReport.Sdk(TckBuildInfo.SDK_NAME, TckBuildInfo.sdkVersion()),
                new ConformanceReport.Tck(
                        TckBuildInfo.IMPLEMENTATION,
                        TckBuildInfo.tckVersion(),
                        TckBuildInfo.specRevision(),
                        TckBuildInfo.assetsTree()),
                backendOf(run),
                capabilitiesOf(declared, failed),
                Collections.unmodifiableList(scenarios));
    }

    private static ConformanceReport.ScenarioResult resolve(
            ScenarioRecord record, Set<Capability> declared, Map<Capability, Integer> failed) {
        Outcome outcome;
        String reason;

        if (record.status == Status.PASSED) {
            outcome = Outcome.PASSED;
            reason = null;
        } else if (record.status == Status.SKIPPED) {
            outcome = Outcome.NOT_DECLARED;
            reason = skipReason(record, declared);
        } else {
            outcome = Outcome.FAILED;
            reason = record.message == null ? "the scenario was reported as " + record.status : record.message;
            for (Capability capability : gatingCapabilities(record.tags)) {
                failed.merge(capability, 1, Integer::sum);
            }
        }

        return new ConformanceReport.ScenarioResult(
                record.feature,
                record.name,
                record.tags.isEmpty() ? null : record.tags,
                outcome,
                reason,
                record.durationMs);
    }

    /**
     * Explains a skip, preferring the capability the scenario needed over whatever was thrown.
     *
     * <p>Deriving the capability from the scenario's own tags rather than from the abort message
     * keeps the two from drifting apart, and gives a consistent sentence across every language's
     * TCK. The thrown message is the fallback, for a scenario skipped by something other than the
     * capability gate.
     */
    private static String skipReason(ScenarioRecord record, Set<Capability> declared) {
        for (Capability capability : gatingCapabilities(record.tags)) {
            if (!declared.contains(capability)) {
                return "requires capability " + capability.tag() + ", which this provider does not declare";
            }
        }
        return record.message == null ? "the scenario was skipped" : record.message;
    }

    private static List<Capability> gatingCapabilities(List<String> tags) {
        List<Capability> gating = new ArrayList<>(tags.size());
        for (String tag : tags) {
            Capability.fromTag(tag).ifPresent(gating::add);
        }
        return gating;
    }

    /**
     * Summarises each capability, with the reason the schema requires for anything but a pass.
     *
     * <p>This object covers the optional contract only, and is not a verdict on the provider: a
     * scenario with no capability tag is mandatory and rolls up into nothing here, so a provider can
     * fail one while every entry below reads {@code passed}. The per-scenario list is what a
     * consumer has to read to decide whether a provider conforms.
     */
    private static Map<String, ConformanceReport.CapabilityResult> capabilitiesOf(
            Set<Capability> declared, Map<Capability, Integer> failed) {
        Map<String, ConformanceReport.CapabilityResult> results = new LinkedHashMap<>();
        for (Capability capability : Capability.values()) {
            ConformanceReport.CapabilityResult result;
            if (!declared.contains(capability)) {
                result = new ConformanceReport.CapabilityResult(
                        Outcome.NOT_DECLARED,
                        "not declared by this provider's configuration; the " + capability.tag()
                                + " scenarios were skipped and did not contribute to this result");
            } else if (failed.containsKey(capability)) {
                int count = failed.get(capability);
                result = new ConformanceReport.CapabilityResult(
                        Outcome.FAILED,
                        count + (count == 1 ? " scenario" : " scenarios") + " carrying " + capability.tag()
                                + " failed; the per-scenario results say which, and why");
            } else {
                result = new ConformanceReport.CapabilityResult(Outcome.PASSED, null);
            }
            results.put(capability.tag(), result);
        }
        return Collections.unmodifiableMap(results);
    }

    private static ConformanceReport.Backend backendOf(TckRunMetadata run) {
        String description = run.backendDescription().orElse(null);
        String controlApi = run.controlApi().orElse(null);
        return description == null && controlApi == null
                ? null
                : new ConformanceReport.Backend(description, controlApi);
    }

    @SuppressFBWarnings(
            value = "PATH_TRAVERSAL_IN",
            justification = "The directory is supplied by whoever started the test run, which is the "
                    + "whole point of the setting; the file name within it is sanitised by ReportNames")
    private void write(ConformanceReport report, String dir, String configuration) {
        Path directory;
        try {
            directory = Paths.get(dir);
        } catch (InvalidPathException e) {
            throw new IllegalStateException(
                    "provider-tck [" + configuration + "]: " + REPORT_DIR_ENV + " is not a usable path: " + dir, e);
        }
        Path path = directory.resolve(ReportNames.fileNameOf(configuration));

        String json;
        try {
            json = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(report) + "\n";
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "provider-tck [" + configuration + "]: could not encode the conformance report", e);
        }

        // A failure to write is raised rather than logged and swallowed. CI that asked for a report
        // and silently did not get one is how a publishing pipeline serves a stale result forever.
        try {
            Files.createDirectories(directory);
            Files.write(path, json.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "provider-tck [" + configuration + "]: could not write the conformance report to " + path, e);
        }

        log.info("provider-tck [{}]: conformance report written to {}", configuration, path);
    }

    private static String messageOf(Result result) {
        Throwable error = result.getError();
        if (error == null) {
            return null;
        }
        String message = error.getMessage();
        return message == null || message.trim().isEmpty() ? error.toString() : message.trim();
    }

    /** Turns {@code classpath:features/errors.feature} into {@code errors}. */
    private static String featureName(TestCase testCase) {
        String uri = testCase.getUri().toString();
        int lastSlash = uri.lastIndexOf('/');
        String base = lastSlash < 0 ? uri : uri.substring(lastSlash + 1);
        int extension = base.lastIndexOf('.');
        return extension <= 0 ? base : base.substring(0, extension);
    }

    /** One scenario as Cucumber reported it, before it is interpreted against declared capabilities. */
    private static final class ScenarioRecord {
        private final String feature;
        private final String name;
        private final List<String> tags;
        private final Status status;
        private final String message;
        private final double durationMs;

        ScenarioRecord(
                String feature, String name, List<String> tags, Status status, String message, double durationMs) {
            this.feature = feature;
            this.name = name;
            this.tags = tags;
            this.status = status;
            this.message = message;
            this.durationMs = durationMs;
        }
    }
}
