package dev.openfeature.contrib.tools.tck;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * What a conformance report needs to know about the run, gathered as the run proceeds.
 *
 * <p>It exists because the two facts a report needs most are known at different times and are gone
 * by the time it is written. The declared capabilities and the backend description come from the
 * harness when the suite starts; the provider's own metadata name is only observable once a scenario
 * has built a provider. Meanwhile Cucumber emits {@code TestRunFinished} — where the file is written
 * — <em>after</em> {@code @AfterAll} has already torn the {@link TckRuntime} down.
 *
 * <p>So the runtime fills this in as it goes and keeps it after {@link TckRuntime#stop()}, and the
 * report plugin reads it at the end.
 *
 * <p>It is also the one place every path to a report passes through, which makes it where the
 * declaration is checked: a {@linkplain Capability#reserved() reserved} capability is rejected here
 * rather than being carried into a document that would then claim it.
 */
final class TckRunMetadata {

    private final String configuration;
    private final Set<Capability> capabilities;
    private final String backendDescription;
    private final String controlApi;
    private final List<KnownDeviation> knownDeviations;

    private volatile String providerName;

    TckRunMetadata(
            String configuration,
            Set<Capability> capabilities,
            String backendDescription,
            String controlApi,
            List<KnownDeviation> knownDeviations) {
        Capability.requireDeclarable(capabilities);
        this.configuration = configuration;
        this.capabilities = capabilities.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(capabilities));
        this.backendDescription = backendDescription;
        this.controlApi = controlApi;
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

    /** Returns how the backend was driven, or empty when there is no control API. */
    Optional<String> controlApi() {
        return Optional.ofNullable(controlApi);
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
