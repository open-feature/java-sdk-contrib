package dev.openfeature.contrib.tools.tck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.cucumber.junit.platform.engine.Constants;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.opentest4j.TestAbortedException;

/**
 * {@link TckIncludeTags} and {@link TckExcludeTags} select extension scenarios, leave canonical
 * scenarios alone, and cannot name a capability tag.
 */
class ExcludedTagsTest {

    private static final URI EXTENSION = URI.create("classpath:extensions/x.feature");

    private static final String UNTAGGED = "An extension scenario with no tags at all";

    @Test
    @DisplayName("an extension scenario runs with an included tag and no excluded one; canonical scenarios always run")
    void scenariosAreSelected() {
        Results results = run(null);

        assertThat(results.passed)
                .containsExactlyInAnyOrder(
                        "A canonical scenario runs without the included tag",
                        "An extension scenario carrying the included tag",
                        "An extension scenario tagged for both modes");
        assertThat(results.aborted)
                .as("the other-mode, untagged, excluded and to-be-filtered scenarios are skipped, saying why")
                .hasSize(4)
                .anySatisfy(reason -> assertThat(reason).contains(ExcludedTagsSuiteFixture.INCLUDED))
                .anySatisfy(reason -> assertThat(reason).contains(ExcludedTagsSuiteFixture.EXCLUDED));
        assertThat(results.failed).isEmpty();
    }

    @Test
    @DisplayName("an untagged extension scenario is skipped under an include, so include filters rather than hints")
    void anUntaggedExtensionScenarioIsNotSpared() {
        Results results = run(null);

        assertThat(results.passed)
                .as(
                        "%s is an include, so an extension scenario carrying no tag at all does not run",
                        ExcludedTagsSuiteFixture.INCLUDED)
                .doesNotContain(UNTAGGED);
        assertThat(results.aborted)
                .as("it is skipped for lacking the included tag, like the other-mode scenarios")
                .filteredOn(reason -> reason.contains(ExcludedTagsSuiteFixture.INCLUDED))
                .hasSize(3);
        assertThat(results.failed).isEmpty();
    }

    @Test
    @DisplayName("a cucumber.filter.tags filter still applies alongside the annotations")
    void aTagFilterStillApplies() {
        Results results = run("not @filtered");

        assertThat(results.passed).hasSize(3);
        assertThat(results.aborted)
                .as("the filtered scenario is gone, not skipped")
                .hasSize(3);
        assertThat(results.failed).isEmpty();
    }

    @Test
    @DisplayName("an excluded tag wins over an included one, and declarations on a superclass add up")
    void excludeWinsAndDeclarationsAddUp() {
        assertThat(AdopterTags.excluded(RpcMode.class)).containsExactly("@fractional-v2", "@fractional-v1");
        assertThat(AdopterTags.included(RpcMode.class)).containsExactly("@rpc");

        assertThatCode(() ->
                        AdopterTags.requireSelected(EXTENSION, Arrays.asList("@rpc", "@in-process"), RpcMode.class))
                .as("a scenario for both modes runs in each")
                .doesNotThrowAnyException();
        assertThatThrownBy(() ->
                        AdopterTags.requireSelected(EXTENSION, Collections.singletonList("@in-process"), RpcMode.class))
                .isInstanceOf(TestAbortedException.class)
                .hasMessageContaining("none of [@rpc]")
                .hasMessageContaining("RpcMode");
        assertThatThrownBy(() ->
                        AdopterTags.requireSelected(EXTENSION, Arrays.asList("@rpc", "@fractional-v1"), RpcMode.class))
                .isInstanceOf(TestAbortedException.class)
                .hasMessageContaining("[@fractional-v1]");
        assertThatCode(() -> AdopterTags.requireSelected(
                        URI.create("classpath:gherkin/errors.feature"), Collections.emptyList(), RpcMode.class))
                .as("a canonical scenario needs no included tag")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("deselecting a scenario cannot mask a reserved tag it carries")
    void theVocabularyIsCheckedBeforeAnySkip() {
        assertThatThrownBy(() -> AdopterTags.requireSelected(
                        EXTENSION, Arrays.asList("@fractional-v1", "@caching"), RpcMode.class))
                .as("the excluded tag would have skipped it; the reserved one still fails the run")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CACHING");
    }

    @Test
    @DisplayName("a capability tag cannot be included or excluded, at suite start or in the scenario gate")
    void aCapabilityTagIsRefused() {
        for (Class<?> harness : Arrays.asList(ExcludesEvents.class, IncludesEvents.class)) {
            assertThatThrownBy(() -> AdopterTags.requireValid(harness))
                    .as(harness.getSimpleName())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(Capability.EVENTS.tag())
                    .hasMessageContaining("capabilities()");
            assertThatThrownBy(() -> AdopterTags.requireSelected(
                            URI.create("classpath:gherkin/events.feature"),
                            Collections.singletonList(Capability.EVENTS.tag()),
                            harness))
                    .as("the scenario gate refuses %s too, canonical scenarios included", harness.getSimpleName())
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("a malformed or contradictory declaration is refused rather than silently matching nothing")
    void aMalformedDeclarationIsRefused() {
        assertThatThrownBy(() -> AdopterTags.requireValid(NoAtSign.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'rpc'");
        assertThatThrownBy(() -> AdopterTags.requireValid(BothWays.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("[@rpc]");
        assertThatCode(() -> AdopterTags.requireValid(RpcMode.class)).doesNotThrowAnyException();
    }

    @TckExcludeTags("@fractional-v1")
    private static class AnyMode {}

    @TckIncludeTags("@rpc")
    @TckExcludeTags("@fractional-v2")
    private static final class RpcMode extends AnyMode {}

    @TckExcludeTags("@events")
    private static final class ExcludesEvents {}

    @TckIncludeTags("@events")
    private static final class IncludesEvents {}

    @TckExcludeTags("rpc")
    private static final class NoAtSign {}

    @TckExcludeTags("@rpc")
    @TckIncludeTags("@rpc")
    private static final class BothWays {}

    private static Results run(String tagFilter) {
        LauncherDiscoveryRequestBuilder request = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectClass(ExcludedTagsSuiteFixture.class));
        if (tagFilter != null) {
            request.configurationParameter(Constants.FILTER_TAGS_PROPERTY_NAME, tagFilter);
        }
        Results results = new Results();
        LauncherFactory.create().execute(request.build(), results);
        return results;
    }

    /** Scenario outcomes; aborted ones keep their reason. */
    private static final class Results implements TestExecutionListener {
        final List<String> passed = Collections.synchronizedList(new ArrayList<>());
        final List<String> aborted = Collections.synchronizedList(new ArrayList<>());
        final List<String> failed = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void executionFinished(TestIdentifier identifier, TestExecutionResult result) {
            if (!identifier.isTest()) {
                return;
            }
            switch (result.getStatus()) {
                case SUCCESSFUL:
                    passed.add(identifier.getDisplayName());
                    break;
                case ABORTED:
                    aborted.add(result.getThrowable().map(Throwable::getMessage).orElse(""));
                    break;
                default:
                    failed.add(identifier.getDisplayName() + ": " + result.getThrowable());
            }
        }
    }
}
