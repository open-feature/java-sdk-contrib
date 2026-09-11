package dev.openfeature.contrib.tools.providertck;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.cucumber.core.plugin.MessageFormatter;
import io.cucumber.messages.types.Envelope;
import io.cucumber.plugin.ConcurrentEventListener;
import io.cucumber.plugin.event.EventPublisher;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
 * {@value #REPORT_DIR_PROPERTY} system property) and each suite writes two files,
 * {@code <dir>/<configuration>.json} and {@code <dir>/<configuration>.ndjson}. Emitting a report is
 * a property of the run rather than of the code — CI asks for one, a developer running the suite
 * locally does not — and unset means no report, which is not an error. Several suites in one JVM each
 * write their own pair of files, so flagd's two resolvers do not collide.
 *
 * <p><strong>The results are not a format this project defines.</strong> The {@code .ndjson} file is
 * a <a href="https://github.com/cucumber/messages">Cucumber Messages</a> stream, produced by
 * Cucumber's own {@link MessageFormatter} — the same class the built-in {@code message:<path>} plugin
 * instantiates, so the bytes are what {@code --plugin message:...} would have written. It already
 * carries everything a per-scenario report would have had to invent: the outcome of every scenario,
 * its tags including any on an individual {@code Examples} block, an exact Scenario Outline row
 * identity through pickle AST node ids, and the {@code source} of every feature that executed.
 *
 * <p>The {@code .json} file is the envelope, and it exists because a Messages stream cannot say what
 * it was a test <em>of</em>. No standard format identifies the provider, the SDK it was driven
 * through, the TCK build, or — most importantly — the capability set the provider declared. That
 * declaration is an input to reading the results rather than a summary of them: the stream says a
 * scenario was skipped, and only the declaration says whether that is because the provider declines
 * the capability it needed.
 *
 * <p><strong>Why the stream is buffered rather than streamed to the file.</strong> The file name is
 * derived from the provider configuration, which is not known when Cucumber wires plugins up — the
 * suite reports it once its runtime has started, which is after the first messages have already been
 * emitted. Buffering keeps one file per suite correctly named, and has the side benefit that
 * {@code results.digest} is computed over exactly the bytes that were written. The stream for this
 * suite is well under a megabyte.
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

    /** Extension of the envelope, which is what a consumer reads first. */
    static final String ENVELOPE_EXTENSION = ".json";

    /** Extension of the Cucumber Messages stream the envelope points at. */
    static final String RESULTS_EXTENSION = ".ndjson";

    private static final Logger log = LoggerFactory.getLogger(ConformanceReportPlugin.class);

    private static final String LANGUAGE = "java";

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
        String dir = reportDir.get();
        if (dir == null) {
            // Nothing asked for a report, so nothing is collected either. Registering the message
            // formatter regardless would buffer a stream for every local test run.
            return;
        }

        ByteArrayOutputStream results = new ByteArrayOutputStream();
        new MessageFormatter(results).setEventPublisher(publisher);

        // Registered *after* the formatter, and deliberately so. Cucumber invokes the handlers for
        // one event type in registration order, and the formatter closes its writer when it sees
        // the run-finished message; going second is what guarantees the buffer is complete and
        // flushed before the digest is taken over it.
        publisher.registerHandlerFor(Envelope.class, envelope -> {
            if (envelope.getTestRunFinished().isPresent()) {
                write(dir, results.toByteArray());
            }
        });
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

    /**
     * Assembles the envelope for what the run observed.
     *
     * @param run what the runtime recorded about this suite
     * @param location where the results stream sits, relative to the envelope
     * @param digest digest over the results stream
     * @return the envelope, ready to serialise
     */
    ConformanceReport build(TckRunMetadata run, String location, String digest, String formatVersion) {
        return new ConformanceReport(
                new ConformanceReport.Provider(run.providerName(), LANGUAGE, run.configuration()),
                new ConformanceReport.Sdk(TckBuildInfo.SDK_NAME, TckBuildInfo.sdkVersion()),
                new ConformanceReport.Tck(
                        TckBuildInfo.IMPLEMENTATION, TckBuildInfo.tckVersion(), TckBuildInfo.specRevision()),
                backendOf(run),
                new ConformanceReport.Declaration(declaredTags(run.capabilities())),
                new ConformanceReport.Results(
                        ConformanceReport.Results.CUCUMBER_MESSAGES, formatVersion, location, digest),
                run.knownDeviations().isEmpty() ? null : run.knownDeviations());
    }

    /**
     * Lists the declared capabilities as Gherkin tags, in the order the vocabulary declares them.
     *
     * <p>Ordered by the enum rather than by the set so that two runs of the same configuration
     * produce byte-identical declarations, which is what makes the envelopes diffable.
     *
     * <p>{@linkplain Capability#reserved() Reserved} capabilities are skipped. A declaration that
     * names one is already rejected where it enters the run, in {@link TckRunMetadata}, so this is
     * not the thing that tells the adopter; it is what makes "no reserved tag in the emitted
     * declaration" a property of the code that writes the document rather than a consequence of a
     * check somewhere upstream of it.
     */
    private static List<String> declaredTags(Set<Capability> declared) {
        List<String> tags = new ArrayList<>(declared.size());
        for (Capability capability : Capability.values()) {
            if (!capability.reserved() && declared.contains(capability)) {
                tags.add(capability.tag());
            }
        }
        return Collections.unmodifiableList(tags);
    }

    private static ConformanceReport.Backend backendOf(TckRunMetadata run) {
        String description = run.backendDescription().orElse(null);
        String controlApi = run.controlApi().orElse(null);
        return description == null && controlApi == null
                ? null
                : new ConformanceReport.Backend(description, controlApi);
    }

    /**
     * Reads the Messages release out of the stream's own {@code meta} message.
     *
     * <p>Taken from the stream rather than from a constant or the {@code io.cucumber:messages}
     * artifact version, because the envelope and the stream disagreeing about which release produced
     * them would be worse than either being absent. {@code meta} is the first envelope cucumber
     * writes, so only the first line is parsed.
     *
     * @param results the buffered results stream
     * @return the protocol version, or {@code null} when the stream carries none
     */
    private static String protocolVersionOf(byte[] results) {
        String stream = new String(results, StandardCharsets.UTF_8);
        int newline = stream.indexOf('\n');
        String first = newline < 0 ? stream : stream.substring(0, newline);
        if (first.trim().isEmpty()) {
            return null;
        }
        try {
            JsonNode version = new ObjectMapper().readTree(first).path("meta").path("protocolVersion");
            return version.isTextual() ? version.asText() : null;
        } catch (JsonProcessingException e) {
            // A stream whose first line will not parse is a bug worth surfacing, but not here:
            // omitting an optional field is better than failing a run that otherwise succeeded.
            log.warn("provider-tck: could not read the Messages protocol version from the stream", e);
            return null;
        }
    }

    @SuppressFBWarnings(
            value = "PATH_TRAVERSAL_IN",
            justification = "The directory is supplied by whoever started the test run, which is the "
                    + "whole point of the setting; the file names within it are sanitised by ReportNames")
    private void write(String dir, byte[] results) {
        Optional<TckRunMetadata> observed = metadata.get();
        if (!observed.isPresent()) {
            // Nothing observed the suite, which means it never got as far as starting the runtime.
            // The run has failed for some other reason by now; adding a report with an invented
            // provider name on top of that would only mislead.
            log.error(
                    "{} is set but no TCK suite ran, so there is nothing to report on. "
                            + "This normally means the suite failed before its backend stack started.",
                    REPORT_DIR_ENV);
            return;
        }

        TckRunMetadata run = observed.get();
        String configuration = run.configuration();
        Path directory;
        try {
            directory = Paths.get(dir);
        } catch (InvalidPathException e) {
            throw new IllegalStateException(
                    "provider-tck [" + configuration + "]: " + REPORT_DIR_ENV + " is not a usable path: " + dir, e);
        }

        String base = ReportNames.baseNameOf(configuration);
        String location = base + RESULTS_EXTENSION;
        Path resultsPath = directory.resolve(location);
        Path envelopePath = directory.resolve(base + ENVELOPE_EXTENSION);

        String json;
        try {
            json = new ObjectMapper()
                            .writerWithDefaultPrettyPrinter()
                            .writeValueAsString(build(run, location, digestOf(results), protocolVersionOf(results)))
                    + "\n";
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "provider-tck [" + configuration + "]: could not encode the conformance report", e);
        }

        // A failure to write is raised rather than logged and swallowed. CI that asked for a report
        // and silently did not get one is how a publishing pipeline serves a stale result forever.
        // The results go first: an envelope naming a stream that is not there is worse than neither.
        try {
            Files.createDirectories(directory);
            Files.write(resultsPath, results);
            Files.write(envelopePath, json.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "provider-tck [" + configuration + "]: could not write the conformance report to " + directory, e);
        }

        log.info(
                "provider-tck [{}]: conformance report written to {}, results to {}",
                configuration,
                envelopePath,
                resultsPath);
    }

    /** Digests the results stream in the {@code sha256:<hex>} form the schema asks for. */
    private static String digestOf(byte[] results) {
        MessageDigest sha256;
        try {
            sha256 = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is required of every Java platform, so this cannot happen on a working JVM.
            throw new IllegalStateException("SHA-256 is not available", e);
        }
        byte[] digest = sha256.digest(results);
        StringBuilder hex = new StringBuilder(7 + digest.length * 2);
        hex.append("sha256:");
        for (byte b : digest) {
            hex.append(Character.forDigit((b >> 4) & 0xf, 16)).append(Character.forDigit(b & 0xf, 16));
        }
        return hex.toString();
    }
}
