package dev.openfeature.contrib.tools.tck;

import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.providers.memory.InMemoryProvider;
import java.util.EnumSet;
import java.util.Set;

/**
 * Runs the OpenFeature Provider TCK against the SDK's own {@link InMemoryProvider}.
 *
 * <p>The <strong>reference adoption</strong> for a provider with no backend — three methods: hand
 * over a {@link BackendControl}, hand over a provider, and say which capabilities hold — and the
 * <strong>Docker-free canary</strong>, which catches a broken step definition or a mis-wired
 * capability gate in seconds rather than through a containerised suite that looks like a provider
 * defect.
 *
 * <p>It is not a licence for providers that have a backend to test themselves this way. See
 * {@link BackendControl}.
 */
public class InMemoryProviderTckTest extends ProviderTckTest {

    /**
     * Both the flag store and the factory for the provider that serves it.
     *
     * <p>In-process the two are the same thing — {@code changeFlag()} has to reach the live provider
     * instance to emit an event from it — so one instance backs both harness methods below.
     */
    private final InProcessBackendControl control = new InProcessBackendControl();

    @Override
    public BackendControl backendControl() {
        return control;
    }

    @Override
    public FeatureProvider createProvider() {
        return control.createProvider();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Each entry was measured against {@link InMemoryProvider} rather than inferred from its
     * source — including {@link Capability#DISABLED_FLAGS} and {@link Capability#STANDARD_REASONS},
     * which hold here and are not a given for an in-memory provider. Each omission is a
     * <em>property</em> of the provider rather than a defect, so none of them leans on the self-test
     * carve-out Appendix F grants these suites; {@link MultiProviderTckTest} is the only suite in
     * this module that does.
     *
     * <ul>
     *   <li>{@link Capability#NUMERIC_COERCION} — the provider keeps the numeric types strictly
     *       apart in both directions, so it refuses the lossless coercions the tag requires. Strict
     *       typing is a choice the SDK's reference provider is entitled to rather than a defect.
     *   <li>{@link Capability#LIFECYCLE} — the flag set arrives through the constructor, so
     *       initialisation acquires nothing and cannot be refused. The scenarios it gates are
     *       covered without Docker by {@link ControllableProviderTckTest}.
     *   <li>{@link Capability#REINITIALIZATION} — never examined here; the scenario it gates carries
     *       {@code @lifecycle} too and is already skipped. Named so that
     *       {@link Capability#declarable()} does not claim it.
     *   <li>{@link Capability#STALE} — no connection to lose, which is also why
     *       {@link InProcessBackendControl} leaves {@link BackendControl#disconnect()}
     *       unimplemented. The omission is what keeps the two consistent: the scenario is skipped
     *       before any step can reach the unsupported operation.
     *   <li>{@link Capability#UNAVAILABLE_INIT} — nothing to connect to, so
     *       {@link ProviderTckHarness#createUnavailableProvider()} stays at its throwing default.
     *   <li>{@link Capability#TARGETING} — the provider evaluates no rules, so a matching context
     *       resolves {@code targeting-key-flag}'s {@code miss} variant like any other.
     *   <li>{@link Capability#CACHING} — reserved, so not declarable.
     * </ul>
     *
     * <p>{@link Capability#LARGE_INTEGERS} is not this suite's to omit: it is
     * {@linkplain Capability#inexpressible() inexpressible} in Java and refused centrally.
     */
    @Override
    public Set<Capability> capabilities() {
        return EnumSet.of(
                Capability.EVENTS,
                Capability.CONFIGURATION_CHANGE,
                Capability.OBJECT,
                Capability.VARIANTS,
                Capability.DISABLED_FLAGS,
                Capability.STRING_TYPING,
                Capability.FULLY_TYPED_VALUES,
                Capability.STANDARD_REASONS);
    }
}
