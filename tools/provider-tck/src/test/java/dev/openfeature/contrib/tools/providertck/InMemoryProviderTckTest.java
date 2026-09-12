package dev.openfeature.contrib.tools.providertck;

import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.providers.memory.InMemoryProvider;
import java.util.EnumSet;
import java.util.Set;

/**
 * Runs the OpenFeature Provider TCK against the SDK's own {@link InMemoryProvider}.
 *
 * <p>This is the TCK's self-test, and it earns its keep twice over.
 *
 * <p>It is the <strong>reference adoption</strong> for a provider with no backend. Everything a
 * file-based or environment-variable provider needs to write is here, and it is three methods: hand
 * over a {@link BackendControl}, hand over a provider, and say which capabilities hold.
 *
 * <p>It is also the <strong>Docker-free canary</strong>. Because it needs no container, no Compose
 * stack and no network, it runs in seconds on any machine and in any CI job, which makes it the
 * fast check that catches a broken step definition, a mis-wired capability gate or a regression in
 * the shared harness long before the containerised suites get a chance to. When a change breaks
 * both this and the flagd suite, this one tells you within seconds and points at the TCK rather
 * than at a provider.
 *
 * <p>Note what it does <em>not</em> do: it is not a licence for providers that have a backend to
 * test themselves this way. See {@link BackendControl} for why.
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
     * <p>Five capabilities, two of which are not obvious. {@link Capability#VARIANTS} holds because
     * {@link InMemoryProvider} does name the variant it served, so the gated variant outline runs
     * and passes here. {@link Capability#DISABLED_FLAGS} holds because it honours a flag's state: the
     * four {@code disabled-*} flags resolve to nothing and the caller's default stands in, with no
     * error code, so all four rows of that outline pass. That is not a given for an in-memory
     * provider — the capability is gated precisely because whether the substitution can happen at all
     * depends on where it happens — and it was measured rather than assumed. Each omission below is a
     * fact about {@link InMemoryProvider} rather than a convenience:
     *
     * <ul>
     *   <li>{@link Capability#NUMERIC_COERCION} — omitted. {@link InMemoryProvider} keeps the two
     *       numeric types strictly apart in both directions: a variant satisfies a request only if
     *       it is an instance of the requested type. That passes the lossy half of the rule — 0.5
     *       requested as an integer is {@code TYPE_MISMATCH} — and fails the lossless half, because
     *       {@code integral-float-flag} (10.0) requested as an integer and {@code integer-flag} (10)
     *       requested as a float are refused just the same, and the tag requires all three. The
     *       rule is borrowed from flagd's ADR rather than from the specification, so strict typing
     *       is a choice the SDK's reference provider is entitled to, not a defect to declare; the
     *       capability is withheld and the three scenarios are skipped with that reason. Declare it
     *       again if the SDK ever adopts the coercion rule.
     *   <li>{@link Capability#LIFECYCLE} — omitted. Initialisation reaches no backend here, so the
     *       readiness scenario would pass without demonstrating anything, which is exactly what that
     *       capability exists to distinguish.
     *   <li>{@link Capability#REINITIALIZATION} — omitted, and nothing turns on it here: the
     *       scenario it gates carries {@code @lifecycle} as well, so it is already skipped for the
     *       omission above. Named anyway, because {@link Capability#declarable()} would have claimed
     *       it and this suite never examined it.
     *   <li>{@link Capability#STALE} — omitted. There is no connection to lose, so the provider can
     *       never go {@code STALE}. {@link InProcessBackendControl} leaves
     *       {@link BackendControl#disconnect()} unimplemented for the same reason, and this omission
     *       is what keeps the two consistent: the scenario is skipped before any step can reach the
     *       unsupported operation.
     *   <li>{@link Capability#UNAVAILABLE_INIT} — omitted. Initialisation cannot fail when there is
     *       nothing to connect to, so
     *       {@link ProviderTckHarness#createUnavailableProvider()} is left at its throwing default.
     *   <li>{@link Capability#TARGETING} — omitted. {@link InMemoryProvider} evaluates no rules: it
     *       reads a flag's {@code variants} and {@code defaultVariant} and returns the default one,
     *       so the {@code targeting} member of {@code targeting-key-flag} is inert here and a
     *       matching context resolves {@code miss} like any other. The three scenarios are skipped
     *       with that reason rather than failed, which is what the tag is for.
     *   <li>{@link Capability#CACHING} — reserved, so not declarable and nothing is skipped by
     *       leaving it out.
     *   <li>{@link Capability#LARGE_INTEGERS} — omitted, as every Java provider omits it. The tag
     *       asks for 2^53 − 1 and {@code Client.getIntegerDetails} is a 32-bit {@link Integer}, so
     *       the limit is the SDK's rather than this provider's; Appendix F is where that is
     *       recorded, and here it is simply undeclared and its scenario skipped.
     * </ul>
     */
    @Override
    public Set<Capability> capabilities() {
        return EnumSet.of(
                Capability.EVENTS,
                Capability.CONFIGURATION_CHANGE,
                Capability.OBJECT,
                Capability.VARIANTS,
                Capability.DISABLED_FLAGS);
    }
}
