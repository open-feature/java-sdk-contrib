package dev.openfeature.contrib.tools.tck;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.gherkin.GherkinParser;
import io.cucumber.messages.types.Envelope;
import io.cucumber.messages.types.Examples;
import io.cucumber.messages.types.Feature;
import io.cucumber.messages.types.FeatureChild;
import io.cucumber.messages.types.GherkinDocument;
import io.cucumber.messages.types.Rule;
import io.cucumber.messages.types.RuleChild;
import io.cucumber.messages.types.Scenario;
import io.cucumber.messages.types.Tag;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every non-reserved capability is carried by a canonical scenario, no reserved one is, and every
 * carried tag is a known capability. Tags are parsed, not scanned as text.
 */
class CanonicalTagCoverageTest {

    /** Tags carried by the canonical feature files this artifact ships, as Gherkin parses them. */
    private static final Set<String> CARRIED = readCarriedTags();

    @Test
    @DisplayName("every capability that is not reserved is carried by at least one canonical scenario")
    void everyUnreservedCapabilityGatesSomething() {
        for (Capability capability : Capability.values()) {
            if (capability.reserved()) {
                continue;
            }
            assertThat(CARRIED)
                    .as(
                            "%s (%s) is not reserved but no canonical scenario carries its tag; packaged "
                                    + "gherkin/ may be stale, or the capability should be reserved",
                            capability.name(), capability.tag())
                    .contains(capability.tag());
        }
    }

    @Test
    @DisplayName("no reserved capability's tag is carried by a canonical scenario")
    void noReservedCapabilityGatesAnything() {
        for (Capability capability : Capability.values()) {
            if (!capability.reserved()) {
                continue;
            }
            assertThat(CARRIED)
                    .as(
                            "%s (%s) is reserved but a canonical scenario carries its tag; drop the " + "reservation",
                            capability.name(), capability.tag())
                    .doesNotContain(capability.tag());
        }
    }

    @Test
    @DisplayName("every tag a canonical scenario carries resolves to a capability")
    void everyCarriedTagIsInTheVocabulary() {
        // reverse of the first test; catches a re-pin that adds a tag without a Capability
        for (String tag : CARRIED) {
            assertThat(Capability.fromTag(tag))
                    .as(
                            "a canonical scenario carries %s, which Capability.fromTag does not resolve; "
                                    + "add it to the Capability enum",
                            tag)
                    .isPresent();
        }
    }

    @Test
    @DisplayName("a tag named only in a Gherkin comment is prose, not a tag")
    void aTagInACommentIsNotCarried() {
        // events.feature names @caching in a comment; a text scan would misreport it
        assertThat(rawCanonicalText())
                .as("events.feature still names @caching in prose")
                .contains("@caching");
        assertThat(CARRIED).as("but nothing carries it as a tag").doesNotContain(Capability.CACHING.tag());
    }

    private static Set<String> readCarriedTags() {
        Set<String> tags = new LinkedHashSet<>();
        forEachCanonicalFeature((name, content) -> {
            GherkinParser parser = GherkinParser.builder()
                    .includeSource(false)
                    .includePickles(false)
                    .includeGherkinDocument(true)
                    .build();
            try (Stream<Envelope> envelopes = parser.parse(name, content)) {
                envelopes.forEach(envelope -> envelope.getGherkinDocument()
                        .flatMap(GherkinDocument::getFeature)
                        .ifPresent(feature -> collectFeature(feature, tags)));
            }
        });
        if (tags.isEmpty()) {
            throw new IllegalStateException("No tags found in the packaged canonical feature files. The "
                    + "artifact is not intact, or gherkin/ was not copied from the spec submodule.");
        }
        return tags;
    }

    private static String rawCanonicalText() {
        StringBuilder all = new StringBuilder();
        forEachCanonicalFeature((name, content) -> all.append(new String(content, StandardCharsets.UTF_8)));
        return all.toString();
    }

    private static void collectFeature(Feature feature, Set<String> tags) {
        addAll(feature.getTags(), tags);
        for (FeatureChild child : feature.getChildren()) {
            child.getScenario().ifPresent(scenario -> collectScenario(scenario, tags));
            child.getRule().ifPresent(rule -> collectRule(rule, tags));
        }
    }

    private static void collectRule(Rule rule, Set<String> tags) {
        addAll(rule.getTags(), tags);
        for (RuleChild child : rule.getChildren()) {
            child.getScenario().ifPresent(scenario -> collectScenario(scenario, tags));
        }
    }

    private static void collectScenario(Scenario scenario, Set<String> tags) {
        addAll(scenario.getTags(), tags);
        for (Examples examples : scenario.getExamples()) {
            addAll(examples.getTags(), tags);
        }
    }

    private static void addAll(List<Tag> from, Set<String> tags) {
        for (Tag tag : from) {
            tags.add(tag.getName());
        }
    }

    /** Reads {@code gherkin/} from the artifact's code source, not the classloader, to avoid shadowing. */
    private static void forEachCanonicalFeature(FeatureConsumer consumer) {
        CodeSource codeSource = ProviderTck.class.getProtectionDomain().getCodeSource();
        if (codeSource == null || codeSource.getLocation() == null) {
            throw new IllegalStateException("The tck code source is not visible to this JVM, so the packaged "
                    + "canonical feature files cannot be read.");
        }
        Path root;
        try {
            root = Paths.get(codeSource.getLocation().toURI());
        } catch (URISyntaxException | IllegalArgumentException e) {
            throw new IllegalStateException("The tck code source is not a file: " + codeSource.getLocation(), e);
        }

        Path features = root.resolve(ProviderTck.FEATURES);
        if (!Files.isDirectory(features)) {
            throw new IllegalStateException("No " + ProviderTck.FEATURES + "/ directory in the tck code source " + root
                    + ". The canonical assets were not copied from the spec submodule.");
        }
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(features, "*.feature")) {
            for (Path entry : entries) {
                consumer.accept(ProviderTck.FEATURES + "/" + entry.getFileName(), Files.readAllBytes(entry));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the canonical feature files from " + features, e);
        }
    }

    @FunctionalInterface
    private interface FeatureConsumer {
        void accept(String name, byte[] content);
    }
}
