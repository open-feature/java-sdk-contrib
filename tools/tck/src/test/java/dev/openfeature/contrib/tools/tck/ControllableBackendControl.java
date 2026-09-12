package dev.openfeature.contrib.tools.tck;

import dev.openfeature.sdk.exceptions.GeneralError;
import dev.openfeature.sdk.providers.memory.Flag;
import java.util.HashMap;
import java.util.Map;

/**
 * In-process control for {@link ControllableProvider}, with a backend that can refuse to answer.
 *
 * <p>Everything {@link InProcessBackendControl} does, plus the one thing it cannot: hand out a
 * provider whose initialisation <em>fails</em>. That is what unlocks the {@code @lifecycle} and
 * {@code @unavailable} scenarios without Docker — an unreachable in-JVM store, rather than a closed
 * socket, is enough for a provider to settle into {@code ERROR} observably and for a shutdown
 * against a dead backend to be timed.
 *
 * <p>Used only by {@link ControllableProviderTckTest}. {@link InProcessBackendControl} remains the
 * one an adopter with no backend writes against, and this does not widen it.
 */
final class ControllableBackendControl implements BackendControl {

    /**
     * The flag {@link #changeFlag()} mutates, named by {@code POST /change} in the control API and
     * by the canonical flag definition, which have to agree.
     */
    private static final String CHANGING_FLAG = "changing-flag";

    /** The canonical flag set, never mutated after construction. */
    private final Map<String, Flag<?>> baseline = CanonicalFlags.flagSet();

    /** {@code changing-flag} as the definition ships it, the source of its variants. */
    private final Flag<?> changingBaseline = requireChangingFlag();

    /** The provider serving the current scenario, or {@code null} between scenarios. */
    private ControllableProvider current;

    /** Which variant {@code changing-flag} currently resolves to. */
    private String changingVariant = changingBaseline.getDefaultVariant();

    /**
     * Creates the provider for the scenario about to run, over a reachable store.
     *
     * <p>The store is read at {@code initialize()} time rather than now, which is the whole reason
     * this provider can cover the lifecycle feature: the flag set is something initialisation
     * acquires.
     *
     * @return a configured, uninitialised provider
     */
    ControllableProvider createProvider() {
        changingVariant = changingBaseline.getDefaultVariant();
        current = new ControllableProvider(this::snapshot);
        return current;
    }

    /**
     * Creates a provider whose backend will not answer, so {@code initialize()} throws.
     *
     * <p>Not registered as {@link #current}: the {@code @unavailable} scenarios never change a flag,
     * and leaving the field alone means a later {@link #changeFlag()} fails loudly rather than
     * mutating a provider that never initialised.
     *
     * @return a provider that cannot initialise
     */
    ControllableProvider createUnavailableProvider() {
        return new ControllableProvider(ControllableBackendControl::unreachable);
    }

    @Override
    public String description() {
        return "in-process control of " + ControllableProvider.class.getSimpleName();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Drops the reference to the previous scenario's provider. The baseline map is never mutated,
     * so the {@link #createProvider()} call that follows starts from an untouched copy of it.
     */
    @Override
    public void prepareScenario() {
        current = null;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Flips {@code changing-flag} between its two variants, and lets the provider emit the event
     * from itself — see {@link ControllableProvider#changeFlag}.
     */
    @Override
    public void changeFlag() {
        changingVariant = otherVariant(changingVariant);
        requireProvider().changeFlag(CHANGING_FLAG, changingFlag(changingVariant));
    }

    /** A fresh copy of the baseline, which is what initialisation acquires. */
    private Map<String, Flag<?>> snapshot() {
        return new HashMap<>(baseline);
    }

    /** The unreachable backend: every attempt to read it fails the way a dead socket would. */
    private static Map<String, Flag<?>> unreachable() {
        throw new GeneralError("the TCK's in-process backend is unreachable for this provider");
    }

    /**
     * Returns a variant of {@code changing-flag} other than the given one.
     *
     * <p>Read out of the definition rather than named here, so that renaming either variant in the
     * spec cannot leave this switching to a name the file no longer defines.
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
            throw new IllegalStateException("The canonical flag definition does not define '" + CHANGING_FLAG
                    + "', which is the flag POST /change mutates and the @configuration-change scenarios evaluate.");
        }
        return flag;
    }

    private ControllableProvider requireProvider() {
        if (current == null) {
            throw new IllegalStateException("No controllable provider exists for this scenario. In-process backend "
                    + "control manipulates the provider itself, so the scenario must create one — with "
                    + "'Given a stable provider' — before any step that changes flag state.");
        }
        return current;
    }
}
