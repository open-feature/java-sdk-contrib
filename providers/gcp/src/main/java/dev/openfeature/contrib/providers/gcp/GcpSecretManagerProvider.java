package dev.openfeature.contrib.providers.gcp;

import com.google.api.gax.rpc.NotFoundException;
import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.Metadata;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Reason;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.FlagNotFoundError;
import dev.openfeature.sdk.exceptions.GeneralError;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * OpenFeature {@link FeatureProvider} backed by Google Cloud Secret Manager.
 *
 * <p>Each feature flag is stored as an individual secret in GCP Secret Manager. The flag key
 * maps directly to the secret name (with an optional prefix configured via
 * {@link GcpProviderOptions#getNamePrefix()}).
 *
 * <p>Flag values are read as UTF-8 strings from the secret payload and parsed to the requested
 * type. Supported raw value formats:
 * <ul>
 *   <li>Boolean: {@code "true"} / {@code "false"} (case-insensitive)</li>
 *   <li>Integer: numeric string, e.g. {@code "42"}</li>
 *   <li>Double: numeric string, e.g. {@code "3.14"}</li>
 *   <li>String: any string value</li>
 *   <li>Object: JSON string that is parsed into an OpenFeature {@link Value}</li>
 * </ul>
 *
 * <p>Results are cached in-process for the duration configured in
 * {@link GcpProviderOptions#getCacheExpiry()}.
 *
 * <p>Example:
 * <pre>{@code
 * GcpSecretManagerProviderOptions opts = GcpSecretManagerProviderOptions.builder()
 *     .projectId("my-gcp-project")
 *     .build();
 * OpenFeatureAPI.getInstance().setProvider(new GcpSecretManagerProvider(opts));
 * }</pre>
 */
@Slf4j
public class GcpSecretManagerProvider implements FeatureProvider {

    static final String PROVIDER_NAME = "GCP Secret Manager Provider";

    private final GcpProviderOptions options;
    private SecretManagerServiceClient client;
    private FlagCache cache;

    /**
     * Creates a new provider using the given options. The GCP client is created lazily
     * during {@link #initialize(EvaluationContext)}.
     *
     * @param options provider configuration; must not be null
     */
    public GcpSecretManagerProvider(GcpProviderOptions options) {
        this.options = options;
    }

    /**
     * Package-private constructor allowing injection of a pre-built client for testing.
     */
    GcpSecretManagerProvider(GcpProviderOptions options, SecretManagerServiceClient client) {
        this.options = options;
        this.client = client;
    }

    @Override
    public Metadata getMetadata() {
        return () -> PROVIDER_NAME;
    }

    @Override
    public void initialize(EvaluationContext evaluationContext) throws Exception {
        options.validate();
        if (client == null) {
            client = SecretManagerClientFactory.create(options);
        }
        cache = new FlagCache(options.getCacheExpiry(), options.getCacheMaxSize());
        log.info("GcpSecretManagerProvider initialized for project '{}'", options.getProjectId());
    }

    @Override
    public void shutdown() {
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                log.warn("Error closing SecretManagerServiceClient", e);
            }
            client = null;
        }
        log.info("GcpSecretManagerProvider shut down");
    }

    @Override
    public ProviderEvaluation<Boolean> getBooleanEvaluation(String key, Boolean defaultValue, EvaluationContext ctx) {
        return evaluate(key, Boolean.class);
    }

    @Override
    public ProviderEvaluation<String> getStringEvaluation(String key, String defaultValue, EvaluationContext ctx) {
        return evaluate(key, String.class);
    }

    @Override
    public ProviderEvaluation<Integer> getIntegerEvaluation(String key, Integer defaultValue, EvaluationContext ctx) {
        return evaluate(key, Integer.class);
    }

    @Override
    public ProviderEvaluation<Double> getDoubleEvaluation(String key, Double defaultValue, EvaluationContext ctx) {
        return evaluate(key, Double.class);
    }

    @Override
    public ProviderEvaluation<Value> getObjectEvaluation(String key, Value defaultValue, EvaluationContext ctx) {
        return evaluate(key, Value.class);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private <T> ProviderEvaluation<T> evaluate(String key, Class<T> targetType) {
        String rawValue = fetchWithCache(key);
        T value = FlagValueConverter.convert(rawValue, targetType);
        return ProviderEvaluation.<T>builder().value(value).reason(Reason.STATIC.toString()).build();
    }

    private String fetchWithCache(String key) {
        String secretName = buildSecretName(key);
        Optional<String> cached = cache.get(secretName);
        if (cached.isPresent()) {
            return cached.get();
        }
        synchronized (this) {
            return cache
                .get(secretName)
                .orElseGet(() -> {
                    String value = fetchFromGcp(secretName);
                    cache.put(secretName, value);
                    return value;
                });
        }
    }

    /**
     * Applies the configured prefix (if any) and returns the GCP secret name for the flag.
     */
    private String buildSecretName(String flagKey) {
        String prefix = options.getNamePrefix();
        return (prefix != null && !prefix.isEmpty()) ? prefix + flagKey : flagKey;
    }

    /**
     * Accesses the configured version of the named secret from GCP Secret Manager.
     *
     * @param secretName the GCP secret name (without project path)
     * @return the UTF-8 string value of the secret payload
     * @throws FlagNotFoundError when the secret does not exist
     * @throws GeneralError      for any other GCP API error
     */
    private String fetchFromGcp(String secretName) {
        try {
            SecretVersionName versionName = SecretVersionName.of(
                options.getProjectId(),
                secretName,
                options.getVersion()
            );
            log.debug("Accessing secret '{}' from GCP", versionName);
            AccessSecretVersionResponse response = client.accessSecretVersion(versionName);
            return response.getPayload().getData().toStringUtf8();
        } catch (NotFoundException e) {
            throw new FlagNotFoundError("Secret not found: " + secretName);
        } catch (Exception e) {
            throw new GeneralError("Error accessing secret '" + secretName + "': " + e.getMessage());
        }
    }
}
