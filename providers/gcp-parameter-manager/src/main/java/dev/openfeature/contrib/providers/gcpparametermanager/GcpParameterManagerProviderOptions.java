package dev.openfeature.contrib.providers.gcpparametermanager;

import com.google.auth.oauth2.GoogleCredentials;
import java.time.Duration;
import lombok.Builder;
import lombok.Getter;

/**
 * Configuration options for {@link GcpParameterManagerProvider}.
 *
 * <p>Example usage:
 * <pre>{@code
 * GcpParameterManagerProviderOptions opts = GcpParameterManagerProviderOptions.builder()
 *     .projectId("my-gcp-project")
 *     .locationId("us-central1")
 *     .cacheExpiry(Duration.ofMinutes(2))
 *     .build();
 * }</pre>
 */
@Getter
@Builder
public class GcpParameterManagerProviderOptions {

    /**
     * GCP project ID that owns the parameters. Required.
     * Example: "my-gcp-project" or numeric project number "123456789".
     */
    private final String projectId;

    /**
     * GCP location for the Parameter Manager endpoint. Optional.
     * Use "global" (default) for the global endpoint, or a region such as "us-central1"
     * when parameters are stored regionally.
     */
    @Builder.Default
    private final String locationId = "global";

    /**
     * Explicit Google credentials to use when creating the Parameter Manager client.
     * When {@code null} (default), Application Default Credentials (ADC) are used
     * automatically by the GCP client library.
     */
    private final GoogleCredentials credentials;

    /**
     * How long a fetched parameter value is retained in the in-memory cache before
     * the next evaluation triggers a fresh GCP API call.
     *
     * <p>GCP Parameter Manager has API quotas. Set this to at least
     * {@code Duration.ofSeconds(30)} in high-throughput scenarios.
     *
     * <p>Default: 5 minutes.
     */
    @Builder.Default
    private final Duration cacheExpiry = Duration.ofMinutes(5);

    /**
     * Maximum number of distinct parameter names held in the cache at once.
     * When the cache is full, the oldest entry is evicted before inserting a new one.
     * Default: 500.
     */
    @Builder.Default
    private final int cacheMaxSize = 500;

    /**
     * The parameter version to retrieve. Defaults to {@code "latest"}.
     * Override with a specific version number (e.g. {@code "3"}) for pinned deployments
     * where you want consistent behaviour regardless of parameter updates.
     */
    @Builder.Default
    private final String parameterVersion = "latest";

    /**
     * Optional prefix prepended to every flag key before constructing the GCP
     * parameter name. For example, setting {@code parameterNamePrefix = "ff-"} maps
     * flag key {@code "my-flag"} to parameter name {@code "ff-my-flag"}.
     */
    private final String parameterNamePrefix;

    /**
     * Validates that required options are present and well-formed.
     *
     * @throws IllegalArgumentException when {@code projectId} is null or blank
     */
    public void validate() {
        if (projectId == null || projectId.trim().isEmpty()) {
            throw new IllegalArgumentException("GcpParameterManagerProviderOptions: projectId must not be blank");
        }
    }
}
