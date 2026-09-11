package dev.openfeature.contrib.tools.providertck;

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
 * <p>A provider that delegates is still a provider, and delegation is where the contract is easiest
 * to drop on the floor: a variant that does not survive the hop, a reason rewritten to
 * {@code DEFAULT}, an error code flattened to {@code GENERAL}, an event that never reaches the
 * client. Wrapping exactly one child makes every one of those observable, because the correct
 * answer is precisely what {@link InMemoryProviderTckTest} already asserts. Any difference between
 * these two suites is attributable to {@link MultiProvider} and nothing else.
 *
 * <p>That framing is the point of running it here rather than in the SDK: this is not a test of
 * aggregation across several backends, it is a test that delegation is transparent.
 *
 * <p>It costs one class, needs no Docker, and it has already earned its place — see the capability
 * note below.
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
     * <p>Exactly one child. See the class javadoc for why that is the interesting configuration
     * rather than a degenerate one.
     */
    @Override
    public FeatureProvider createProvider() {
        return new MultiProvider(Collections.singletonList(control.createProvider()));
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@link Capability#CONFIGURATION_CHANGE} is <strong>not</strong> declared, and that is a
     * finding rather than a configuration choice.
     *
     * <p>{@link MultiProvider} extends {@code EventProvider} but never subscribes to its children,
     * so a child's {@code PROVIDER_CONFIGURATION_CHANGED} — along with its {@code PROVIDER_ERROR}
     * and {@code PROVIDER_STALE} — is swallowed and never reaches the client. Wrapping an
     * in-memory provider in a multi-provider therefore silently costs you configuration-change
     * events, with nothing in the API to suggest it.
     *
     * <p>This is a known gap, tracked as
     * <a href="https://github.com/open-feature/java-sdk/issues/1882">open-feature/java-sdk#1882</a>
     * (gap 1, "child provider event aggregation and status tracking", High). The suite reproduced
     * it from the outside, which is a reasonable advertisement for what the TCK is for: the gap was
     * originally found by hand-comparing implementations against the js-sdk reference.
     *
     * <p><strong>Delete this omission once #1882 is fixed.</strong> Until then the
     * {@code @configuration-change} scenario is reported as skipped-with-reason rather than passing
     * on a provider that cannot satisfy it.
     *
     * <p>Everything else holds. Values, variants, reasons, the full type-mismatch matrix,
     * {@code FLAG_NOT_FOUND}, falsy values, 32-bit integer precision and structured values all
     * survive the delegation hop unchanged — {@link Capability#VARIANTS} is declared for exactly
     * that reason, and a variant lost in delegation is one of the likelier ways a facade breaks the
     * contract. {@link Capability#LIFECYCLE} and {@link Capability#NUMERIC_COERCION} are omitted for
     * the same reasons as in {@link InMemoryProviderTckTest}: nothing here reaches a backend during
     * initialisation, and the child refuses the lossless coercions the tag now requires — a facade
     * cannot declare what its only child does not have. {@link Capability#TARGETING} is omitted for
     * the same reason again: the child evaluates no rules, so there is no targeting to delegate.
     */
    @Override
    public Set<Capability> capabilities() {
        return EnumSet.of(Capability.EVENTS, Capability.OBJECT, Capability.VARIANTS);
    }
}
