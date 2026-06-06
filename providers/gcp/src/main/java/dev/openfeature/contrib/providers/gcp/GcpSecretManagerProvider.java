package dev.openfeature.contrib.providers.gcp;

import com.google.api.gax.rpc.NotFoundException;
import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.FlagNotFoundError;
import dev.openfeature.sdk.exceptions.GeneralError;
import lombok.extern.slf4j.Slf4j;

/**
 * OpenFeature {@link FeatureProvider} backed by Google Cloud Secret Manager.
 *
 * <p>Each feature flag is stored as an individual secret in GCP Secret Manager. The flag key
 * maps directly to the secret name (with an optional prefix configured via
 * {@code GcpProviderOptions#getNamePrefix()}).
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
 * {@code GcpProviderOptions#getCacheExpiry()}.
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
public class GcpSecretManagerProvider extends AbstractGcpProvider<SecretManagerServiceClient> {

    static final String PROVIDER_NAME = "GCP Secret Manager Provider";

    /**
     * Creates a new provider using the given options. The GCP client is created lazily
     * during {@link #initialize(EvaluationContext)}.
     *
     * @param options provider configuration; must not be null
     */
    public GcpSecretManagerProvider(GcpProviderOptions options) {
        super(options);
    }

    /**
     * Package-private constructor allowing injection of a pre-built client for testing.
     */
    GcpSecretManagerProvider(GcpProviderOptions options, SecretManagerServiceClient client) {
        super(options, client);
    }

    @Override
    protected String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    protected void createClient() throws Exception {
        this.client = SecretManagerClientFactory.create(options);
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
    protected String fetchFromGcp(String secretName) {
        try {
            SecretVersionName versionName =
                    SecretVersionName.of(options.getProjectId(), secretName, options.getVersion());
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
