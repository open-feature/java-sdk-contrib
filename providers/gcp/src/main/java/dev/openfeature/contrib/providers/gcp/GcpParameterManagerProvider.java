package dev.openfeature.contrib.providers.gcp;

import com.google.api.gax.rpc.NotFoundException;
import com.google.cloud.parametermanager.v1.ParameterManagerClient;
import com.google.cloud.parametermanager.v1.ParameterVersionName;
import com.google.cloud.parametermanager.v1.RenderParameterVersionResponse;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.FlagNotFoundError;
import dev.openfeature.sdk.exceptions.GeneralError;
import lombok.extern.slf4j.Slf4j;

/**
 * OpenFeature {@link FeatureProvider} backed by Google Cloud Parameter Manager.
 *
 * <p>Each feature flag is stored as an individual parameter in GCP Parameter Manager. The flag
 * key maps directly to the parameter name (with an optional prefix configured via
 * {@link GcpProviderOptions#getParameterNamePrefix()}).
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
 * {@link GcpProviderOptions#getCacheExpiry()}.
 *
 * <p>Example:
 * <pre>{@code
 * GcpProviderOptions opts = GcpProviderOptions.builder()
 *     .projectId("my-gcp-project")
 *     .build();
 * OpenFeatureAPI.getInstance().setProvider(new GcpParameterManagerProvider(opts));
 * }</pre>
 */
@Slf4j
public class GcpParameterManagerProvider extends AbstractGcpProvider<ParameterManagerClient> {

    static final String PROVIDER_NAME = "GCP Parameter Manager Provider";

    /**
     * Creates a new provider using the given options. The GCP client is created lazily
     * during {@link #initialize(EvaluationContext)}.
     *
     * @param options provider configuration; must not be null
     */
    public GcpParameterManagerProvider(GcpProviderOptions options) {
        super(options);
    }

    /**
     * Package-private constructor allowing injection of a pre-built client for testing.
     */
    GcpParameterManagerProvider(GcpProviderOptions options, ParameterManagerClient client) {
        super(options, client);
    }

    @Override
    protected String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    protected void createClient() throws Exception {
        this.client = ParameterManagerClientFactory.create(options);
    }

    @Override
    protected void closeClient() throws Exception {
        this.client.close();
    }

    @Override
    public void initialize(EvaluationContext evaluationContext) throws Exception {
        super.initialize(evaluationContext);
        log.info("{} initialized via initialize()", getProviderName());
    }

    @Override
    public void shutdown() {
        super.shutdown();
        log.info("{} shutdown via shutdown()", getProviderName());
    }

    @Override
    protected String fetchFromGcp(String parameterName) {
        try {
            ParameterVersionName versionName = ParameterVersionName.of(
                    options.getProjectId(), options.getLocationId(), parameterName, options.getVersion());
            RenderParameterVersionResponse response = client.renderParameterVersion(versionName);
            return response.getRenderedPayload().toStringUtf8();
        } catch (NotFoundException e) {
            throw new FlagNotFoundError("Parameter not found: " + parameterName);
        } catch (Exception e) {
            throw new GeneralError("Error fetching parameter '" + parameterName + "': " + e.getMessage(), e);
        }
    }
}
