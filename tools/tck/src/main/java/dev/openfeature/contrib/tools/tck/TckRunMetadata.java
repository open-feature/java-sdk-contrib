package dev.openfeature.contrib.tools.tck;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * What a conformance report needs to know about the run, gathered as the run proceeds.
 *
 * <p>It exists because the two facts a report needs most are known at different times and are gone
 * by the time it is written. The declared capabilities and the backend description come from the
 * harness when the suite starts; the provider's own metadata name is only observable once a scenario
 * has built a provider. Meanwhile Cucumber emits {@code TestRunFinished} — where the file is written
 * — <em>after</em> {@code @AfterAll} has already torn the suite's backend down.
 *
 * <p>So the suite lifecycle records one of these when a suite starts and it outlives the suite, and
 * the report plugin reads it at the end. Public only for that lifecycle, which lives in the internal
 * steps package: its two static entry points are not for adopters.
 *
 * <p>It is also the one place every path to a report passes through, which makes it where the
 * declaration is checked: a {@linkplain Capability#reserved() reserved} capability is rejected here
 * rather than being carried into a document that would then claim it.
 */
public final class TckRunMetadata {

    /** The most recent suite to start, kept after it stops for the report written at run end. */
    private static final AtomicReference<TckRunMetadata> last = new AtomicReference<>();

    private final String configuration;
    private final Set<Capability> capabilities;
    private final String backendDescription;
    private final BackendType backendType;
    private final List<KnownDeviation> knownDeviations;

    private volatile String providerName;

    TckRunMetadata(
            String configuration,
            Set<Capability> capabilities,
            String backendDescription,
            BackendType backendType,
            List<KnownDeviation> knownDeviations) {
        Capability.requireDeclarable(capabilities);
        this.configuration = configuration;
        this.capabilities = capabilities.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(capabilities));
        this.backendDescription = backendDescription;
        this.backendType = backendType;
        this.knownDeviations = Collections.unmodifiableList(new ArrayList<>(knownDeviations));
    }

    /** Returns the suite name, which is the provider configuration under test. */
    String configuration() {
        return configuration;
    }

    /** Returns the capabilities the harness declared. */
    Set<Capability> capabilities() {
        return capabilities;
    }

    /** Returns a short description of the backend stack, or empty when there is none. */
    Optional<String> backendDescription() {
        return Optional.ofNullable(backendDescription);
    }

    /**
     * Records the suite that is starting, as the run the conformance report describes.
     *
     * @param harness the suite's harness
     * @param control the control its backend is driven through
     */
    public static void begin(ProviderTckHarness harness, BackendControl control) {
        record(new TckRunMetadata(
                harness.configuration(),
                harness.capabilities(),
                control.description(),
                control.backendType(),
                harness.knownDeviations()));
    }

    /**
     * Records what the provider under test calls itself, against the suite that is running.
     *
     * <p>A conformance report identifies the provider by its own metadata name rather than by the
     * suite's, and only a scenario that has built one can say what that is.
     *
     * @param name the provider's metadata name
     */
    public static void observeProviderName(String name) {
        TckRunMetadata run = last.get();
        if (run != null) {
            run.recordProviderName(name);
        }
    }

    /**
     * Replaces the recorded run. Also the seam the report tests use to describe a run without a
     * backend.
     *
     * @param metadata what the run is, or {@code null} to forget the last one
     */
    static void record(TckRunMetadata metadata) {
        last.set(metadata);
    }

    /** Returns the most recent suite to start, or empty if none has. */
    static Optional<TckRunMetadata> last() {
        return Optional.ofNullable(last.get());
    }

    /**
     * Returns how the backend was driven, as the control stated it; {@link BackendType#CUSTOM} for
     * a control that states nothing.
     */
    BackendType backendType() {
        return backendType;
    }

    /** Returns the deviations the provider author acknowledged, empty when there are none. */
    List<KnownDeviation> knownDeviations() {
        return knownDeviations;
    }

    /**
     * Records what the provider called itself, as observed from a scenario that built one.
     *
     * @param name the provider's own metadata name
     */
    void recordProviderName(String name) {
        if (name != null && !name.trim().isEmpty()) {
            providerName = name;
        }
    }

    /**
     * Returns the provider's own metadata name, falling back to the configuration name.
     *
     * <p>The fallback covers a run in which no scenario ever built a provider — every scenario
     * skipped, or the stack never came up. Reporting the suite name there is more useful than the
     * empty string the schema would reject.
     *
     * @return the name to report as the provider's identity
     */
    String providerName() {
        String observed = providerName;
        return observed == null ? configuration : observed;
    }
}
