package dev.openfeature.contrib.providers.gcpparametermanager;

import com.google.api.gax.rpc.NotFoundException;
import com.google.cloud.parametermanager.v1.ParameterManagerClient;
import com.google.cloud.parametermanager.v1.ParameterVersionName;
import com.google.cloud.parametermanager.v1.RenderParameterVersionResponse;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.Metadata;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Reason;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.FlagNotFoundError;
import dev.openfeature.sdk.exceptions.GeneralError;
import lombok.extern.slf4j.Slf4j;

/**
 * OpenFeature {@link FeatureProvider} backed by Google Cloud Parameter Manager.
 *
 * <p>Each feature flag is stored as an individual parameter in GCP Parameter Manager. The flag
 * key maps directly to the parameter name (with an optional prefix configured via
 * {@link GcpParameterManagerProviderOptions#getParameterNamePrefix()}).
 *
 * <p>Flag values are read as strings and parsed to the requested type. Supported raw value
 * formats:
 * <ul>
 *   <li>Boolean: {@code "true"} / {@code "false"} (case-insensitive)</li>
 *   <li>Integer: numeric string, e.g. {@code "42"}</li>
 *   <li>Double: numeric string, e.g. {@code "3.14"}</li>
 *   <li>String: any string value</li>
 *   <li>Object: JSON string that is parsed into an OpenFeature {@link Value}</li>
 * </ul>
 *
 * <p>Results are cached in-process for the duration configured in
 * {@link GcpParameterManagerProviderOptions#getCacheExpiry()}.
 *
 * <p>Example:
 * <pre>{@code
 * GcpParameterManagerProviderOptions opts = GcpParameterManagerProviderOptions.builder()
 *     .projectId("my-gcp-project")
 *     .build();
 * OpenFeatureAPI.getInstance().setProvider(new GcpParameterManagerProvider(opts));
 * }</pre>
 */
@Slf4j
public class GcpParameterManagerProvider implements FeatureProvider {

    static final String PROVIDER_NAME = "GCP Parameter Manager Provider";

    private final GcpParameterManagerProviderOptions options;
    private ParameterManagerClient client;
    private FlagCache cache;

    /**
     * Creates a new provider using the given options. The GCP client is created lazily
     * during {@link #initialize(EvaluationContext)}.
     *
     * @param options provider configuration; must not be null
     */
    public GcpParameterManagerProvider(GcpParameterManagerProviderOptions options) {
        this.options = options;
    }

    /**
     * Package-private constructor allowing injection of a pre-built client for testing.
     */
    GcpParameterManagerProvider(GcpParameterManagerProviderOptions options, ParameterManagerClient client) {
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
            client = ParameterManagerClientFactory.create(options);
        }
        cache = new FlagCache(options.getCacheExpiry(), options.getCacheMaxSize());
        log.info("GcpParameterManagerProvider initialized for project '{}'", options.getProjectId());
    }

    @Override
    public void shutdown() {
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                log.warn("Error closing ParameterManagerClient", e);
            }
            client = null;
        }
        log.info("GcpParameterManagerProvider shut down");
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
        return ProviderEvaluation.<T>builder()
                .value(value)
                .reason(Reason.STATIC.toString())
                .build();
    }

    private String fetchWithCache(String key) {
        String paramName = buildParameterName(key);
        return cache.get(paramName).orElseGet(() -> {
            String value = fetchFromGcp(paramName);
            cache.put(paramName, value);
            return value;
        });
    }

    /**
     * Applies the configured prefix (if any) and returns the GCP parameter name for the flag.
     */
    private String buildParameterName(String flagKey) {
        String prefix = options.getParameterNamePrefix();
        return (prefix != null && !prefix.isEmpty()) ? prefix + flagKey : flagKey;
    }

    /**
     * Fetches the latest version of the named parameter from GCP Parameter Manager.
     *
     * @param parameterName the GCP parameter name (without project/location path)
     * @return the rendered string value of the parameter
     * @throws FlagNotFoundError when the parameter does not exist
     * @throws GeneralError      for any other GCP API error
     */
    private String fetchFromGcp(String parameterName) {
        try {
            ParameterVersionName versionName =
                    ParameterVersionName.of(options.getProjectId(), options.getLocationId(), parameterName, options.getParameterVersion());
            log.debug("Fetching parameter '{}' from GCP", versionName);
            RenderParameterVersionResponse response = client.renderParameterVersion(versionName);
            return response.getRenderedPayload().toStringUtf8();
        } catch (NotFoundException e) {
            throw new FlagNotFoundError("Parameter not found: " + parameterName);
        } catch (Exception e) {
            throw new GeneralError("Error fetching parameter '" + parameterName + "': " + e.getMessage());
        }
    }
}
