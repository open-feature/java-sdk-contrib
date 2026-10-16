package dev.openfeature.contrib.tools.tck;

import dev.openfeature.sdk.exceptions.GeneralError;
import dev.openfeature.sdk.providers.memory.Flag;
import java.util.HashMap;
import java.util.Map;

/**
 * In-process control for {@link ControllableProvider}, with a backend that can refuse to answer.
 * Unlocks the {@code @lifecycle} and {@code @unavailable} scenarios without Docker.
 */
final class ControllableBackendControl implements BackendControl {

    /** The flag {@link #changeFlag()} mutates, per the control API's {@code POST /change}. */
    private static final String CHANGING_FLAG = "changing-flag";

    /** The canonical flag set, never mutated after construction. */
    private final Map<String, Flag<?>> baseline = CanonicalFlags.flagSet();

    /** {@code changing-flag} as the definition ships it, the source of its variants. */
    private final Flag<?> changingBaseline = requireChangingFlag();

    /** The provider serving the current scenario, or {@code null} between scenarios. */
    private ControllableProvider current;

    /** Which variant {@code changing-flag} currently resolves to. */
    private String changingVariant = changingBaseline.getDefaultVariant();

    /** Creates the provider for the next scenario; the store is read at {@code initialize()} time. */
    ControllableProvider createProvider() {
        changingVariant = changingBaseline.getDefaultVariant();
        current = new ControllableProvider(this::snapshot);
        return current;
    }

    /** Creates a provider whose {@code initialize()} throws; not registered as {@link #current}. */
    ControllableProvider createUnavailableProvider() {
        return new ControllableProvider(ControllableBackendControl::unreachable);
    }

    @Override
    public String description() {
        return "in-process control of " + ControllableProvider.class.getSimpleName();
    }

    @Override
    public ControlApi controlApi() {
        return ControlApi.IN_PROCESS;
    }

    /** Drops the previous scenario's provider; the baseline is never mutated. */
    @Override
    public void prepareScenario() {
        current = null;
    }

    /** Flips {@code changing-flag} between its two variants. */
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

    /** A variant of {@code changing-flag} other than the given one, read from the definition. */
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
                    + "control manipulates the provider itself, so the scenario must create one, with "
                    + "'Given a stable provider', before any step that changes flag state.");
        }
        return current;
    }
}
