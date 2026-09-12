package dev.openfeature.contrib.tools.tck;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.cucumber.gherkin.GherkinParser;
import io.cucumber.messages.types.Envelope;
import io.cucumber.messages.types.Examples;
import io.cucumber.messages.types.Feature;
import io.cucumber.messages.types.FeatureChild;
import io.cucumber.messages.types.GherkinDocument;
import io.cucumber.messages.types.Rule;
import io.cucumber.messages.types.RuleChild;
import io.cucumber.messages.types.Scenario;
import io.cucumber.messages.types.TableRow;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * The canonical scenario set, as this artifact ships it.
 *
 * <p>Read from <em>this artifact's own code source</em> — the JAR or {@code target/classes} that
 * {@link ProviderTck} was loaded from — rather than through the classloader. That is the point of the
 * class: a feature file placed in {@code gherkin/} on another classpath root shadows the canonical
 * one of the same name, and a check that read the shadowed copy back through
 * {@code getResource("gherkin/errors.feature")} would be checking the replacement against itself.
 * The code source is the only view of {@code gherkin/} that an adopter's classpath cannot alter.
 *
 * <p>A scenario is identified by its resource name and its line, which is what a Cucumber
 * {@code ClasspathResourceSource} in the JUnit test plan carries. For a Scenario Outline that line is
 * the {@code Examples} row, so each row counts individually — which is necessary, because eleven rows
 * of the type-mismatch matrix share one name.
 *
 * <p>What this does <strong>not</strong> establish is that the canonical files contain what they
 * should. A replacement file that happened to place its scenarios on the same lines would satisfy the
 * comparison. Content is covered elsewhere and better: the results stream carries the {@code source}
 * of every feature that executed, and the report's {@code tck.specRevision} says which revision it
 * should match.
 */
final class CanonicalScenarios {

    private static final String FEATURE_SUFFIX = ".feature";

    private static volatile Set<Ref> shipped;

    private CanonicalScenarios() {}

    /**
     * Returns every scenario the canonical feature files in this artifact compile to.
     *
     * @return the canonical scenario set, never empty
     * @throws IllegalStateException if this artifact's own feature files cannot be read
     */
    static Set<Ref> shipped() {
        Set<Ref> cached = shipped;
        if (cached == null) {
            synchronized (CanonicalScenarios.class) {
                if (shipped == null) {
                    shipped = Collections.unmodifiableSet(read());
                }
                cached = shipped;
            }
        }
        return cached;
    }

    /** Reads and compiles the canonical feature files out of the code source this class came from. */
    @SuppressFBWarnings(
            value = "PATH_TRAVERSAL_IN",
            justification = "The path is this class's own code source, reported by the JVM. Nothing outside the "
                    + "running artifact can influence it, and reading it from anywhere else would defeat the point "
                    + "of the class")
    private static Set<Ref> read() {
        CodeSource codeSource = ProviderTck.class.getProtectionDomain().getCodeSource();
        URL location = codeSource == null ? null : codeSource.getLocation();
        if (location == null) {
            throw new IllegalStateException("The tck code source is not visible to this JVM, so the "
                    + "canonical scenario set cannot be established. Set -D" + CanonicalScenarioGuard.PARTIAL_PROPERTY
                    + "=true to run without the canonical-set check, understanding that the run is then not a "
                    + "conformance run.");
        }

        Path path;
        try {
            path = Paths.get(location.toURI());
        } catch (URISyntaxException | IllegalArgumentException e) {
            throw new IllegalStateException(
                    "The tck code source " + location + " is not a file, so the "
                            + "canonical scenario set cannot be established.",
                    e);
        }

        Set<Ref> refs = new LinkedHashSet<>();
        try {
            if (Files.isDirectory(path)) {
                fromDirectory(path, refs);
            } else {
                fromJar(path, refs);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the canonical feature files from " + path, e);
        }

        if (refs.isEmpty()) {
            throw new IllegalStateException("No canonical scenarios found in " + ProviderTck.FEATURES + "/ of the "
                    + "tck artifact at " + path + ". The artifact is not intact.");
        }
        return refs;
    }

    private static void fromDirectory(Path root, Set<Ref> refs) throws IOException {
        Path features = root.resolve(ProviderTck.FEATURES);
        if (!Files.isDirectory(features)) {
            return;
        }
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(features, "*" + FEATURE_SUFFIX)) {
            for (Path entry : entries) {
                String name = ProviderTck.FEATURES + "/" + entry.getFileName();
                collect(name, Files.readAllBytes(entry), refs);
            }
        }
    }

    private static void fromJar(Path jar, Set<Ref> refs) throws IOException {
        try (JarFile file = new JarFile(jar.toFile())) {
            Enumeration<JarEntry> entries = file.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!entry.isDirectory()
                        && name.startsWith(ProviderTck.FEATURES + "/")
                        && name.endsWith(FEATURE_SUFFIX)) {
                    try (InputStream in = file.getInputStream(entry)) {
                        collect(name, readAll(in), refs);
                    }
                }
            }
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    /**
     * Compiles one feature file to the scenarios Cucumber would run from it.
     *
     * <p>The Gherkin document is walked rather than the pickles it produces, because a pickle carries
     * no line number — and the line is what the JUnit test plan identifies a scenario by. The walk is
     * the same rule Gherkin's own compiler applies: a Scenario Outline yields one scenario per
     * {@code Examples} body row, anything else yields one at its own line.
     */
    private static void collect(String resource, byte[] content, Set<Ref> refs) {
        GherkinParser parser = GherkinParser.builder()
                .includeSource(false)
                .includePickles(false)
                .includeGherkinDocument(true)
                .build();

        try (Stream<Envelope> envelopes = parser.parse(resource, content)) {
            envelopes.forEach(envelope -> envelope.getGherkinDocument()
                    .flatMap(GherkinDocument::getFeature)
                    .ifPresent(feature -> collectFeature(resource, feature, refs)));
        }
    }

    private static void collectFeature(String resource, Feature feature, Set<Ref> refs) {
        for (FeatureChild child : feature.getChildren()) {
            child.getScenario().ifPresent(scenario -> collectScenario(resource, scenario, refs));
            child.getRule().ifPresent(rule -> collectRule(resource, rule, refs));
        }
    }

    private static void collectRule(String resource, Rule rule, Set<Ref> refs) {
        for (RuleChild child : rule.getChildren()) {
            child.getScenario().ifPresent(scenario -> collectScenario(resource, scenario, refs));
        }
    }

    private static void collectScenario(String resource, Scenario scenario, Set<Ref> refs) {
        List<Examples> examples = scenario.getExamples();
        if (examples.isEmpty()) {
            refs.add(new Ref(resource, scenario.getLocation().getLine(), scenario.getName()));
            return;
        }
        for (Examples block : examples) {
            for (TableRow row : block.getTableBody()) {
                refs.add(new Ref(resource, row.getLocation().getLine(), scenario.getName()));
            }
        }
    }

    /**
     * One scenario, identified the way the JUnit test plan identifies it.
     *
     * <p>The name is carried for the failure message only; two scenarios are the same scenario when
     * they are at the same line of the same classpath resource.
     */
    static final class Ref implements Comparable<Ref> {

        private final String resource;
        private final long line;
        private final String name;

        Ref(String resource, long line, String name) {
            this.resource = resource;
            this.line = line;
            this.name = name;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Ref)) {
                return false;
            }
            Ref that = (Ref) other;
            return line == that.line && resource.equals(that.resource);
        }

        @Override
        public int hashCode() {
            return 31 * resource.hashCode() + Long.hashCode(line);
        }

        @Override
        public int compareTo(Ref other) {
            int byResource = resource.compareTo(other.resource);
            return byResource != 0 ? byResource : Long.compare(line, other.line);
        }

        @Override
        public String toString() {
            return resource + ":" + line + (name == null || name.isEmpty() ? "" : " (" + name + ")");
        }
    }
}
