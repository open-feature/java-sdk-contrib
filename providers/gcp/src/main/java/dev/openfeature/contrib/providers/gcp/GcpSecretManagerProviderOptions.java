package dev.openfeature.contrib.providers.gcpsecretmanager;

import com.google.auth.oauth2.GoogleCredentials;
import java.time.Duration;
import lombok.Builder;
import lombok.Getter;

/**
 * Configuration options for {@link GcpSecretManagerProvider}.
 *
 * <p>Example usage:
 * <pre>{@code
 * GcpSecretManagerProviderOptions opts = GcpSecretManagerProviderOptions.builder()
 *     .projectId("my-gcp-project")
 *     .secretVersion("latest")
 *     .cacheExpiry(Duration.ofMinutes(2))
 *     .build();
 * }</pre>
 */
@Getter
@Builder
public class GcpSecretManagerProviderOptions {

    /**
     * GCP project ID that owns the secrets. Required.
     * Example: "my-gcp-project" or numeric project number "123456789".
     */
    private final String projectId;

    /**
     * Explicit Google credentials to use when creating the Secret Manager client.
     * When {@code null} (default), Application Default Credentials (ADC) are used
     * automatically by the GCP client library.
     */
    private final GoogleCredentials credentials;

    /**
     * The secret version to retrieve. Defaults to {@code "latest"}.
     * Override with a specific version number (e.g. {@code "3"}) for pinned deployments
     * where you want consistent behaviour regardless of secret rotation.
     */
    @Builder.Default
    private final String secretVersion = "latest";

    /**
     * How long a fetched secret value is retained in the in-memory cache before
     * the next evaluation triggers a fresh GCP API call.
     *
     * <p>Secret Manager has API quotas (10,000 access operations per minute per project
     * by default). Set this to at least {@code Duration.ofSeconds(30)} in
     * high-throughput scenarios.
     *
     * <p>Default: 5 minutes.
     */
    @Builder.Default
    private final Duration cacheExpiry = Duration.ofMinutes(5);

    /**
     * Maximum number of distinct secret names held in the cache at once.
     * When the cache is full, the oldest entry is evicted before inserting a new one.
     * Default: 500.
     */
    @Builder.Default
    private final int cacheMaxSize = 500;

    /**
     * Optional prefix prepended to every flag key before constructing the GCP
     * secret name. For example, setting {@code secretNamePrefix = "ff-"} maps
     * flag key {@code "my-flag"} to secret name {@code "ff-my-flag"}.
     */
    private final String secretNamePrefix;

    /**
     * Validates that required options are present and well-formed.
     *
     * @throws IllegalArgumentException when {@code projectId} is null or blank
     */
    public void validate() {
        if (projectId == null || projectId.trim().isEmpty()) {
            throw new IllegalArgumentException("GcpSecretManagerProviderOptions: projectId must not be blank");
        }
        if (secretVersion == null || secretVersion.trim().isEmpty()) {
            throw new IllegalArgumentException("GcpSecretManagerProviderOptions: secretVersion must not be blank");
        }
    }
}
