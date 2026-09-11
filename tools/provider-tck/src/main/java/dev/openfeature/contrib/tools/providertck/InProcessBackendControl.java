package dev.openfeature.contrib.tools.providertck;

import dev.openfeature.sdk.MutableStructure;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.providers.memory.Flag;
import dev.openfeature.sdk.providers.memory.InMemoryProvider;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
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

    /** The flag {@link #changeFlag()} mutates, as defined by {@code flags/canonical-flags.json}. */
    private static final String CHANGING_FLAG = "changing-flag";

    private static final String CHANGING_BASELINE = "foo";
    private static final String CHANGING_CHANGED = "bar";

    /**
     * The canonical flag set, never mutated after construction.
     *
     * <p>Scenario isolation depends on that: {@link InMemoryProvider} copies the map it is given,
     * and {@code updateFlag} writes only to the provider's copy, so every provider handed out by
     * {@link #createProvider()} starts from an untouched baseline.
     */
    private final Map<String, Flag<?>> baseline = canonicalFlags();

    /** The provider serving the current scenario, or {@code null} between scenarios. */
    private InMemoryProvider current;

    /** Which variant {@code changing-flag} currently resolves to. */
    private String changingVariant = CHANGING_BASELINE;

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
        changingVariant = CHANGING_BASELINE;
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
        changingVariant = CHANGING_CHANGED.equals(changingVariant) ? CHANGING_BASELINE : CHANGING_CHANGED;
        requireProvider().updateFlag(CHANGING_FLAG, changingFlag(changingVariant));
    }

    private InMemoryProvider requireProvider() {
        if (current == null) {
            throw new IllegalStateException("No in-memory provider exists for this scenario. In-process backend "
                    + "control manipulates the provider itself, so the scenario must create one — with "
                    + "'Given a stable provider' — before any step that changes flag state.");
        }
        return current;
    }

    /**
     * Builds the canonical flag set as {@link InMemoryProvider} flags.
     *
     * <p>Mirrors {@code flags/canonical-flags.json} entry for entry. The load-bearing details from
     * that file hold here too:
     *
     * <ul>
     *   <li>{@code missing-flag} is absent, which is what the {@code FLAG_NOT_FOUND} scenario tests;
     *   <li>no flag carries a {@link dev.openfeature.sdk.providers.memory.ContextEvaluator}, so
     *       every evaluation reports reason {@code STATIC} as the feature files expect;
     *   <li>{@code false-flag}, {@code zero-flag} and {@code empty-string-flag} resolve to
     *       {@code false}, {@code 0} and {@code ""} — values, not absences;
     *   <li>{@code integral-float-flag} is a {@link Double} holding {@code 10.0}, never the
     *       {@link Integer} {@code 10}, or the lossless-coercion scenario would pass without
     *       anything being coerced; {@code huge-integer-flag} is a {@link Long}, because
     *       2^53 − 1 does not fit an {@link Integer}.
     * </ul>
     *
     * @return the canonical flag set
     */
    private static Map<String, Flag<?>> canonicalFlags() {
        Map<String, Flag<?>> flags = new LinkedHashMap<>();

        flags.put(
                "boolean-flag",
                Flag.<Boolean>builder()
                        .variant("on", true)
                        .variant("off", false)
                        .defaultVariant("on")
                        .build());

        flags.put(
                "string-flag",
                Flag.<String>builder()
                        .variant("greeting", "hi")
                        .variant("parting", "bye")
                        .defaultVariant("greeting")
                        .build());

        flags.put(
                "integer-flag",
                Flag.<Integer>builder()
                        .variant("one", 1)
                        .variant("ten", 10)
                        .defaultVariant("ten")
                        .build());

        flags.put(
                "float-flag",
                Flag.<Double>builder()
                        .variant("tenth", 0.1)
                        .variant("half", 0.5)
                        .defaultVariant("half")
                        .build());

        // 2^31 - 1: the largest value every language's integer accessor can ask for, and one a
        // float32 round trip does not keep.
        flags.put(
                "large-integer-flag",
                Flag.<Integer>builder()
                        .variant("one", 1)
                        .variant("max-int32", 2147483647)
                        .defaultVariant("max-int32")
                        .build());

        // 2^53 - 1, which does not fit an Integer and so is a Long. Only asked for under
        // @large-integers, which is not applicable in Java, so no scenario reaches it; it is here
        // so that the set mirrors the JSON entry for entry, seeded as an integer and not rounded.
        flags.put(
                "huge-integer-flag",
                Flag.<Long>builder()
                        .variant("one", 1L)
                        .variant("max-safe", 9007199254740991L)
                        .defaultVariant("max-safe")
                        .build());

        // A float with no fractional part, for the lossless half of @numeric-coercion. The literal
        // 10.0 is a double, so the variant is a Double and stays one.
        flags.put(
                "integral-float-flag",
                Flag.<Double>builder()
                        .variant("tenth", 0.1)
                        .variant("ten", 10.0)
                        .defaultVariant("ten")
                        .build());

        // The three falsy values. Each scenario's default differs from the resolved value, so a
        // provider that treats false, 0 or "" as "nothing came back" is caught.
        flags.put(
                "false-flag",
                Flag.<Boolean>builder()
                        .variant("on", true)
                        .variant("off", false)
                        .defaultVariant("off")
                        .build());

        flags.put(
                "zero-flag",
                Flag.<Integer>builder()
                        .variant("one", 1)
                        .variant("zero", 0)
                        .defaultVariant("zero")
                        .build());

        flags.put(
                "empty-string-flag",
                Flag.<String>builder()
                        .variant("greeting", "hi")
                        .variant("empty", "")
                        .defaultVariant("empty")
                        .build());

        flags.put(
                "object-flag",
                Flag.<Value>builder()
                        .variant("empty", new Value(new MutableStructure()))
                        .variant(
                                "template",
                                new Value(new MutableStructure()
                                        .add("showImages", true)
                                        .add("title", "Check out these pics!")
                                        .add("imagesPerPage", 100)))
                        .defaultVariant("template")
                        .build());

        // A string flag, evaluated as a boolean by the TYPE_MISMATCH scenario.
        flags.put(
                "wrong-flag",
                Flag.<String>builder()
                        .variant("one", "uno")
                        .variant("two", "dos")
                        .defaultVariant("one")
                        .build());

        flags.put(CHANGING_FLAG, changingFlag(CHANGING_BASELINE));

        return Collections.unmodifiableMap(flags);
    }

    private static Flag<String> changingFlag(String defaultVariant) {
        return Flag.<String>builder()
                .variant(CHANGING_BASELINE, CHANGING_BASELINE)
                .variant(CHANGING_CHANGED, CHANGING_CHANGED)
                .defaultVariant(defaultVariant)
                .build();
    }
}
