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
 * Every capability this suite does not call reserved is carried by at least one canonical scenario,
 * every reserved one is carried by none, and every tag they carry is a capability this suite knows.
 *
 * <p>{@link CapabilityGate#requireNoExpiredReservation} already fails a run where a <em>scenario</em>
 * carries a reserved tag — a capability no adopter may declare, gating something, so the scenario is
 * skipped forever and nothing notices. This is the other half of the same rule: a capability this
 * suite says has scenarios that gates <strong>nothing</strong>. Declarable, that is a claim no result
 * can contradict, which tells a report's reader that a capability was examined when nothing examined
 * it. That is the same vacuous claim, arrived at from the opposite direction.
 *
 * <p>The first test is over every capability that is not reserved, rather than over
 * {@link Capability#declarable()}, and the difference matters. An
 * {@linkplain Capability#inexpressible() inexpressible} capability is not declarable here, but its
 * scenarios are precisely what distinguish it from a reservation: they exist, and other languages
 * run them. Checking only the declarable set would stop looking at the one capability whose whole
 * justification is that the scenarios are there.
 *
 * <p>It has one realistic cause, and it is a build accident rather than a design mistake: the
 * canonical assets are copied out of the {@code spec} submodule at {@code generate-resources}, and
 * the submodule's working tree and the gitlink are moved by different commands. A rebase or a branch
 * switch updates the gitlink; only {@code git submodule update} moves the checkout. Between the two,
 * the copy step happily overwrites the new assets with the old ones, and the result is internally
 * consistent — the old feature files agree with each other — so <em>counting</em> scenarios does not
 * catch it. A capability added in the same commit as the pin that gives it scenarios is then
 * declarable, and gates nothing.
 *
 * <p>This is a test rather than a runtime check on purpose. The reserved direction has to fail an
 * adopter's run, because a reservation expires when the specification writes scenarios for it and
 * this package may not have followed. This direction can only be introduced by a build of
 * <em>this</em> artifact, so it belongs in this artifact's own tests — and making every adopter parse
 * six feature files at suite start to detect a mistake only this repository can make would be a cost
 * paid in the wrong place.
 *
 * <p><strong>The tags are parsed, not scanned.</strong> {@code gherkin/events.feature} names
 * {@code @caching} inside a Gherkin {@code #} comment, explaining which stale-provider behaviour is
 * deliberately uncovered, so a text scan reports a reserved tag that no scenario carries. The third
 * test below asserts exactly that, so the distinction is pinned rather than described.
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
                            "%s (%s) is not reserved, so this suite says scenarios for it exist — but no "
                                    + "canonical scenario carries its tag. Either the packaged gherkin/ is stale "
                                    + "(check that the spec submodule working tree matches the gitlink: git -C "
                                    + "tools/tck/spec rev-parse HEAD) or the capability was added ahead of its "
                                    + "scenarios, in which case mark it reserved until they arrive.",
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
                            "%s (%s) is still marked reserved, but a canonical scenario now carries its tag. "
                                    + "The reservation has expired: drop the reserved flag so an adopter can "
                                    + "declare it and be held to it. CapabilityGate fails such a scenario at "
                                    + "run time; this says it at build time.",
                            capability.name(), capability.tag())
                    .doesNotContain(capability.tag());
        }
    }

    @Test
    @DisplayName("every tag a canonical scenario carries resolves to a capability")
    void everyCarriedTagIsInTheVocabulary() {
        // The reverse of the first test, and the direction that is easy to leave out: that one
        // catches a capability with no scenarios, this one a scenario tag with no capability. An
        // unknown tag gates nothing, so its scenarios stay mandatory for every adopter — a suite
        // that has not learned a new capability keeps demanding the old behaviour, and the only
        // symptom is a provider that legitimately withholds it failing while the rest stay green.
        //
        // This fires on the re-pin that adds a tag, which is the moment it is needed: the pin and
        // the Capability constant move in the same commit, and nothing else notices if only the
        // pin moves. CapabilityGate.requireKnownVocabulary is the same rule at run time, for the
        // canonical assets an adopter actually executes rather than the ones packaged here.
        for (String tag : CARRIED) {
            assertThat(Capability.fromTag(tag))
                    .as(
                            "A canonical scenario carries %s, which Capability.fromTag does not resolve. The "
                                    + "pinned specification revision has a capability this package does not: add "
                                    + "it to the Capability enum and say in its javadoc what declaring it claims. "
                                    + "Until then the tag gates nothing and its scenarios are mandatory for every "
                                    + "adopter, including the ones that cannot support it.",
                            tag)
                    .isPresent();
        }
    }

    @Test
    @DisplayName("a tag named only in a Gherkin comment is prose, not a tag")
    void aTagInACommentIsNotCarried() {
        // The trap that makes a text scan wrong on day one, asserted rather than described.
        // events.feature explains what @caching would cover, inside a comment; a grep-shaped
        // implementation of the test above would report CACHING as an expired reservation forever.
        assertThat(rawCanonicalText())
                .as("events.feature still names @caching in prose, which is what makes this test worth having")
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

    /**
     * Reads {@code gherkin/} out of this artifact's own code source, as {@link ProviderTck} was
     * loaded from, rather than through the classloader — the same rule the canonical set is read by,
     * and for the same reason: a feature file placed in {@code gherkin/} on another classpath root
     * shadows the canonical one, and a check that read the shadowed copy would be checking the
     * replacement against itself.
     */
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
