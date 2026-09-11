package dev.openfeature.contrib.tools.providertck;

import dev.openfeature.sdk.providers.memory.Flag;
import dev.openfeature.sdk.providers.memory.InMemoryProvider;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.HashMap;
import java.util.Map;

/**
 * {@link BackendControl} that manipulates the SDK's {@link InMemoryProvider} directly, with no
 * backend, no container and no HTTP.
 *
 * <p>This exists so that providers with nothing to connect to — in-memory, environment-variable and
 * file-based providers — can run the TCK. For those, "the backend" is a data structure in the same
 * JVM: seeding flags is building a map, and changing one is
 * {@link InMemoryProvider#updateFlag(String, Flag)}, which emits
 * {@code PROVIDER_CONFIGURATION_CHANGED} through the provider's own event mechanism rather than
 * through a simulated one.
 *
 * <p><strong>This is not a shortcut for providers that do have a backend.</strong> Reaching into an
 * external backend from inside the JVM — a test-only admin client, a shared database handle, a
 * static hook in the provider — produces a suite that passes while proving nothing, because the
 * path it exercised is not the path the contract describes. Those providers use
 * {@link HttpBackendControl} via {@link ContainerizedProviderTckTest}, and the control API in
 * {@code openapi/control-api.yaml} stays the normative contract. See {@link BackendControl}.
 *
 * <h2>Connection control</h2>
 *
 * <p>{@link #disconnect()}, {@link #reconnect()} and {@link #disconnectFor} are not implemented, so
 * they inherit the interface defaults and throw. An in-memory provider has no connection to lose,
 * and pretending otherwise with a no-op would report {@code @stale} scenarios as passed. The
 * harness instead leaves {@link Capability#STALE} and {@link Capability#UNAVAILABLE_INIT}
 * undeclared, and those scenarios are reported as skipped.
 *
 * <h2>Ownership of the provider</h2>
 *
 * <p>This class both seeds the flags and creates the provider that serves them, because in-process
 * they are the same object: {@link #changeFlag()} has to reach the live provider instance to emit
 * an event from it. A harness therefore wires both of its factory methods to one instance:
 *
 * <pre>{@code
 * private final InProcessBackendControl control = new InProcessBackendControl();
 *
 * @Override
 * public BackendControl backendControl() {
 *     return control;
 * }
 *
 * @Override
 * public FeatureProvider createProvider() {
 *     return control.createProvider();
 * }
 * }</pre>
 */
public final class InProcessBackendControl implements BackendControl {

    /**
     * The flag {@link #changeFlag()} mutates, as named by the control API and by the canonical flag
     * definition.
     *
     * <p>The key is the one thing about this flag that is not read out of the definition, because
     * {@code POST /change} in {@code openapi/control-api.yaml} names it too: the two have to agree,
     * and a key discovered from the file could not be checked against the contract that uses it. Its
     * variants are read, and {@link #changingFlag} rebuilds it from them.
     */
    private static final String CHANGING_FLAG = "changing-flag";

    /**
     * The canonical flag set, never mutated after construction.
     *
     * <p>Decoded from the packaged {@code flags/canonical-flags.json} rather than restated here —
     * see {@link CanonicalFlags} for why a transcription is the failure mode this guards against.
     *
     * <p>Scenario isolation depends on the map not being mutated: {@link InMemoryProvider} copies the
     * map it is given, and {@code updateFlag} writes only to the provider's copy, so every provider
     * handed out by {@link #createProvider()} starts from an untouched baseline.
     */
    private final Map<String, Flag<?>> baseline = CanonicalFlags.flagSet();

    /** {@code changing-flag} as the definition ships it, the source of its variants. */
    private final Flag<?> changingBaseline = requireChangingFlag();

    /** The provider serving the current scenario, or {@code null} between scenarios. */
    private InMemoryProvider current;

    /** Which variant {@code changing-flag} currently resolves to. */
    private String changingVariant = changingBaseline.getDefaultVariant();

    /**
     * Creates the provider for the scenario about to run, seeded with the canonical flag set.
     *
     * <p>Each call returns a fresh instance over a fresh copy of the baseline, which is what makes
     * {@link #prepareScenario()} nothing more than dropping the previous reference.
     *
     * @return a configured, uninitialised in-memory provider
     */
    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP",
            justification = "Handing out the live provider is the contract, not a leak: in-process "
                    + "the flag store and the provider are one object, and changeFlag() must reach "
                    + "the same instance the TCK registered in order to emit an event from it")
    public InMemoryProvider createProvider() {
        changingVariant = changingBaseline.getDefaultVariant();
        current = new InMemoryProvider(new HashMap<>(baseline));
        return current;
    }

    @Override
    public String description() {
        return "in-process control of " + InMemoryProvider.class.getSimpleName();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Drops the reference to the previous scenario's provider. That is the whole reset: the
     * baseline map is never mutated, so the {@link #createProvider()} call that follows produces a
     * provider already at the baseline. Clearing the reference rather than leaving it dangling
     * means a scenario that manipulates flags without creating a provider fails with a clear
     * message instead of mutating a provider that has already been shut down.
     */
    @Override
    public void prepareScenario() {
        current = null;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Flips {@code changing-flag} between its two variants through
     * {@link InMemoryProvider#updateFlag(String, Flag)}, so the event the suite awaits is the
     * provider's own {@code PROVIDER_CONFIGURATION_CHANGED} — carrying {@code changing-flag} in
     * {@code flagsChanged} — and not a signal the TCK synthesised.
     *
     * <p>Alternating rather than assigning a fixed variant keeps repeated calls within one scenario
     * meaningful; the suite asserts that the resolved value differs, not what it became.
     */
    @Override
    public void changeFlag() {
        changingVariant = otherVariant(changingVariant);
        requireProvider().updateFlag(CHANGING_FLAG, changingFlag(changingVariant));
    }

    /**
     * Returns a variant of {@code changing-flag} other than the given one.
     *
     * <p>Read out of the definition rather than named here, so that renaming either variant in the
     * spec cannot leave this switching between a name the file no longer defines and one it does.
     */
    private String otherVariant(String resolved) {
        for (String variant : changingBaseline.getVariants().keySet()) {
            if (!variant.equals(resolved)) {
                return variant;
            }
        }
        throw new IllegalStateException("The canonical definition of '" + CHANGING_FLAG + "' has only the variant '"
                + resolved + "'. changeFlag() has to switch to a different one, so the flag needs at least two.");
    }

    /** Rebuilds {@code changing-flag} with a different variant as the one it resolves to. */
    private Flag<?> changingFlag(String defaultVariant) {
        return Flag.builder()
                .variants(changingBaseline.getVariants())
                .defaultVariant(defaultVariant)
                .disabled(changingBaseline.isDisabled())
                .build();
    }

    /** The canonical definition of {@code changing-flag}, which the suite cannot do without. */
    private Flag<?> requireChangingFlag() {
        Flag<?> flag = baseline.get(CHANGING_FLAG);
        if (flag == null) {
            throw new IllegalStateException("The canonical flag definition " + CanonicalFlags.RESOURCE
                    + " does not define '" + CHANGING_FLAG + "', which is the flag POST /change mutates and the "
                    + "@configuration-change scenarios evaluate.");
        }
        return flag;
    }

    private InMemoryProvider requireProvider() {
        if (current == null) {
            throw new IllegalStateException("No in-memory provider exists for this scenario. In-process backend "
                    + "control manipulates the provider itself, so the scenario must create one — with "
                    + "'Given a stable provider' — before any step that changes flag state.");
        }
        return current;
    }
}
