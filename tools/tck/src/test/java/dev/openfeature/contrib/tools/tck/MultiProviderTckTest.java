package dev.openfeature.contrib.tools.tck;

import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.multiprovider.MultiProvider;
import dev.openfeature.sdk.providers.memory.InMemoryProvider;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Runs the OpenFeature Provider TCK against the SDK's {@link MultiProvider}, wrapping a single
 * {@link InMemoryProvider}.
 *
 * <p>Delegation is where the contract is easiest to drop on the floor: a variant that does not
 * survive the hop, a reason rewritten to {@code DEFAULT}, an error code flattened to
 * {@code GENERAL}, an event that never reaches the client. Wrapping exactly <em>one</em> child makes
 * each of those observable, because the correct answer is precisely what
 * {@link InMemoryProviderTckTest} already asserts, so any difference between the two suites is
 * attributable to {@link MultiProvider} and nothing else. This is a test that delegation is
 * transparent rather than a test of aggregation.
 */
public class MultiProviderTckTest extends ProviderTckTest {

    private final InProcessBackendControl control = new InProcessBackendControl();

    @Override
    public BackendControl backendControl() {
        return control;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Exactly one child — see the class javadoc.
     */
    @Override
    public FeatureProvider createProvider() {
        return new MultiProvider(Collections.singletonList(control.createProvider()));
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@link Capability#CONFIGURATION_CHANGE} is <strong>not</strong> declared, and that is a
     * finding rather than a configuration choice: {@link MultiProvider} extends
     * {@code EventProvider} but never subscribes to its children, so a child's
     * {@code PROVIDER_CONFIGURATION_CHANGED}, {@code PROVIDER_ERROR} and {@code PROVIDER_STALE} are
     * swallowed and never reach the client. Tracked as
     * <a href="https://github.com/open-feature/java-sdk/issues/1882">open-feature/java-sdk#1882</a>.
     * <strong>Delete this omission once #1882 is fixed.</strong>
     *
     * <p>It is also the one omission in this module that rests on Appendix F's self-test carve-out,
     * whose condition — that the defect is pinned by a test of its own — is met only partly: nothing
     * here asserts the swallowed event directly, so a reader has this javadoc and the skip reason
     * and no executing assertion.
     *
     * <p>Everything else survives the delegation hop unchanged, including the variants, reasons and
     * disabled-flag substitution that a facade is most likely to break, which is why
     * {@link Capability#VARIANTS}, {@link Capability#DISABLED_FLAGS} and
     * {@link Capability#STANDARD_REASONS} are declared. The remaining omissions are
     * {@link InMemoryProviderTckTest}'s, since a facade cannot declare what its only child does not
     * have.
     */
    @Override
    public Set<Capability> capabilities() {
        return EnumSet.of(
                Capability.EVENTS,
                Capability.OBJECT,
                Capability.VARIANTS,
                Capability.DISABLED_FLAGS,
                Capability.STRING_TYPING,
                Capability.FULLY_TYPED_VALUES,
                Capability.STANDARD_REASONS);
    }
}
