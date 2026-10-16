package dev.openfeature.contrib.tools.tck;

import dev.openfeature.sdk.providers.memory.Flag;
import dev.openfeature.sdk.providers.memory.InMemoryProvider;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.HashMap;
import java.util.Map;

/**
 * {@link BackendControl} that manipulates the SDK's {@link InMemoryProvider} directly. Only for
 * providers with no backend; others use {@link ContainerizedProviderTckTest}.
 *
 * <p>{@link #disconnect()} and {@link #reconnect()} throw, so leave {@link Capability#STALE} and
 * {@link Capability#UNAVAILABLE_INIT} undeclared. Wire both harness factory methods to one instance:
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

    // flag mutated by changeFlag(); also named by POST /change
    private static final String CHANGING_FLAG = "changing-flag";

    // canonical flag set; never mutated, which provides scenario isolation
    private final Map<String, Flag<?>> baseline = CanonicalFlags.flagSet();

    // changing-flag as defined, source of its variants
    private final Flag<?> changingBaseline = requireChangingFlag();

    // provider for the current scenario, null between scenarios
    private InMemoryProvider current;

    // variant changing-flag currently resolves to
    private String changingVariant = changingBaseline.getDefaultVariant();

    /**
     * Creates a fresh provider for the next scenario, seeded with the canonical flag set.
     *
     * @return a configured, uninitialised in-memory provider
     */
    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP",
            justification = "changeFlag() must reach the same live instance the TCK registered")
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
     * <p>Always {@link ControlApi#IN_PROCESS}.
     */
    @Override
    public ControlApi controlApi() {
        return ControlApi.IN_PROCESS;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Drops the previous provider; the next {@link #createProvider()} starts from the baseline.
     */
    @Override
    public void prepareScenario() {
        current = null;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Flips {@code changing-flag} between variants via
     * {@link InMemoryProvider#updateFlag(String, Flag)}, which emits the provider's own event.
     */
    @Override
    public void changeFlag() {
        changingVariant = otherVariant(changingVariant);
        requireProvider().updateFlag(CHANGING_FLAG, changingFlag(changingVariant));
    }

    /** Returns a variant of {@code changing-flag} other than the given one. */
    private String otherVariant(String resolved) {
        for (String variant : changingBaseline.getVariants().keySet()) {
            if (!variant.equals(resolved)) {
                return variant;
            }
        }
        throw new IllegalStateException("The canonical definition of '" + CHANGING_FLAG + "' has only the variant '"
                + resolved + "'; changeFlag() needs at least two.");
    }

    /** Rebuilds {@code changing-flag} resolving to the given variant. */
    private Flag<?> changingFlag(String defaultVariant) {
        return Flag.builder()
                .variants(changingBaseline.getVariants())
                .defaultVariant(defaultVariant)
                .disabled(changingBaseline.isDisabled())
                .build();
    }

    /** Returns the canonical {@code changing-flag}, failing if absent. */
    private Flag<?> requireChangingFlag() {
        Flag<?> flag = baseline.get(CHANGING_FLAG);
        if (flag == null) {
            throw new IllegalStateException("The canonical flag definition " + CanonicalFlags.RESOURCE
                    + " does not define '" + CHANGING_FLAG + "', required by POST /change.");
        }
        return flag;
    }

    private InMemoryProvider requireProvider() {
        if (current == null) {
            throw new IllegalStateException("No in-memory provider exists for this scenario; use "
                    + "'Given a stable provider' before any step that changes flag state.");
        }
        return current;
    }
}
