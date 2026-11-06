package dev.openfeature.contrib.tools.tck;

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
 * <p>Registered automatically by {@link ProviderTckTest}. Opt-in per run: set {@value #REPORT_DIR_ENV}
 * (or the {@value #REPORT_DIR_PROPERTY} system property) and each suite writes
 * {@code <dir>/<configuration>.json}, the envelope, and {@code <dir>/<configuration>.ndjson}, a
 * <a href="https://github.com/cucumber/messages">Cucumber Messages</a> stream produced by Cucumber's
 * own {@link MessageFormatter}. Unset means no report, which is not an error.
 *
 * <p>The stream is buffered rather than written as it arrives, because the file name derives from the
 * provider configuration, which is only known once the suite's runtime has started. That also makes
 * {@code results.digest} exactly the bytes written.
 *
 * @see <a href="https://github.com/open-feature/spec/issues/424">open-feature/spec#424</a>
 */
public final class ConformanceReportPlugin implements ConcurrentEventListener {

    /** Environment variable naming the directory reports are written to. */
    public static final String REPORT_DIR_ENV = "TCK_REPORT_DIR";

    /**
     * System property naming the directory reports are written to, taking precedence over the
     * environment variable, which is the spelling every other language's TCK reads.
     */
    public static final String REPORT_DIR_PROPERTY = "tck.report.dir";

    /** Extension of the envelope. */
    static final String ENVELOPE_EXTENSION = ".json";

    /** Extension of the Cucumber Messages stream the envelope points at. */
    static final String RESULTS_EXTENSION = ".ndjson";

    private static final Logger log = LoggerFactory.getLogger(ConformanceReportPlugin.class);

    private static final String LANGUAGE = "java";

    private final Supplier<String> reportDir;
    private final Supplier<Optional<TckRunMetadata>> metadata;

    /** Creates the plugin Cucumber instantiates by name. */
    public ConformanceReportPlugin() {
        this(ConformanceReportPlugin::configuredReportDir, TckRunMetadata::last);
    }

    ConformanceReportPlugin(Supplier<String> reportDir, Supplier<Optional<TckRunMetadata>> metadata) {
        this.reportDir = reportDir;
        this.metadata = metadata;
    }

    @Override
    public void setEventPublisher(EventPublisher publisher) {
        String dir = reportDir.get();
        if (dir == null) {
            // Without this, the formatter would buffer a stream for every local test run.
            return;
        }

        ByteArrayOutputStream results = new ByteArrayOutputStream();
        new MessageFormatter(results).setEventPublisher(publisher);

        // Must be registered after the formatter: handlers run in registration order, and the
        // formatter flushes the buffer on the run-finished message.
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
     * Lists the declared capabilities as Gherkin tags in enum order, so envelopes of the same
     * configuration are byte-identical. {@linkplain Capability#reserved() Reserved} capabilities are
     * skipped.
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

    /** Assembles the {@code backend} block, which the schema requires. */
    private static ConformanceReport.Backend backendOf(TckRunMetadata run) {
        return new ConformanceReport.Backend(run.backendDescription().orElse(null), run.backendType());
    }

    /**
     * Reads the Messages release out of the stream's {@code meta} message, which is the first line.
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
            // Optional field: omit it rather than fail a run that otherwise succeeded.
            log.warn("tck: could not read the Messages protocol version from the stream", e);
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
            // The suite never started its runtime, so the run has already failed for another reason.
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
                    "tck [" + configuration + "]: " + REPORT_DIR_ENV + " is not a usable path: " + dir, e);
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
            throw new IllegalStateException("tck [" + configuration + "]: could not encode the conformance report", e);
        }

        // Raised, not logged: CI that asked for a report must not silently go without one. Results go
        // first, so an envelope never names a stream that is not there.
        try {
            Files.createDirectories(directory);
            Files.write(resultsPath, results);
            Files.write(envelopePath, json.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "tck [" + configuration + "]: could not write the conformance report to " + directory, e);
        }

        log.info("tck [{}]: conformance report written to {}, results to {}", configuration, envelopePath, resultsPath);
    }

    /** Digests the results stream in the {@code sha256:<hex>} form the schema asks for. */
    private static String digestOf(byte[] results) {
        MessageDigest sha256;
        try {
            sha256 = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
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
