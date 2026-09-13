package dev.openfeature.contrib.tools.tck;

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
     * <p>Six capabilities, three of which are not obvious. {@link Capability#VARIANTS} holds because
     * {@link InMemoryProvider} does name the variant it served, so the gated variant outline runs
     * and passes here. {@link Capability#DISABLED_FLAGS} holds because it honours a flag's state: the
     * four {@code disabled-*} flags resolve to nothing and the caller's default stands in, with no
     * error code, so all four rows of that outline pass. That is not a given for an in-memory
     * provider — the capability is gated precisely because whether the substitution can happen at all
     * depends on where it happens — and it was measured rather than assumed.
     *
     * <p>{@link Capability#STANDARD_REASONS} is the third, and it was measured the same way rather
     * than inferred from the provider's source. {@link InMemoryProvider} reports {@code STATIC} for a
     * rule-less flag, {@code ERROR} beside {@code FLAG_NOT_FOUND} and {@code TYPE_MISMATCH}, and
     * {@code DISABLED} for a disabled flag, so seven of {@code reason.feature}'s nine scenarios run
     * and pass. The other two carry {@code @targeting} as well and are skipped for that omission —
     * the tag composition doing its job, since a provider that evaluates no rules has no
     * {@code TARGETING_MATCH} to report and failing it for the absence would say nothing. Each
     * omission below is a fact about {@link InMemoryProvider} rather than a convenience:
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
     *   <li>{@link Capability#LIFECYCLE} — omitted. {@link InMemoryProvider} is handed its whole
     *       flag set by its constructor, so initialisation acquires nothing and cannot be refused,
     *       and the readiness scenario would pass without demonstrating anything — which is exactly
     *       what that capability exists to distinguish. The six scenarios it gates are covered
     *       without Docker by {@link ControllableProviderTckTest}, whose provider does acquire its
     *       store at {@code initialize()} time.
     *   <li>{@link Capability#REINITIALIZATION} — omitted, and nothing turns on it here: the
     *       scenario it gates carries {@code @lifecycle} as well, so it is already skipped for the
     *       omission above. Named anyway, because {@link Capability#declarable()} would have claimed
     *       it and this suite never examined it. {@link ControllableProviderTckTest} does.
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
     * </ul>
     *
     * <p>{@link Capability#LARGE_INTEGERS} is not in that list and is not this suite's to omit:
     * it is {@linkplain Capability#inexpressible() inexpressible} in Java, so
     * {@link Capability#requireDeclarable} refuses it and its scenario is skipped for a reason that
     * names the SDK. That used to be a bullet here, and an identical one in every other suite in
     * this repository.
     */
    @Override
    public Set<Capability> capabilities() {
        return EnumSet.of(
                Capability.EVENTS,
                Capability.CONFIGURATION_CHANGE,
                Capability.OBJECT,
                Capability.VARIANTS,
                Capability.DISABLED_FLAGS,
                Capability.STANDARD_REASONS);
    }
}
