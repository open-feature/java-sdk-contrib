package dev.openfeature.contrib.providers.gcpparametermanager;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.cloud.parametermanager.v1.ParameterManagerClient;
import com.google.cloud.parametermanager.v1.ParameterManagerSettings;
import java.io.IOException;

/**
 * Factory for creating a {@link ParameterManagerClient}, separated to allow injection
 * of mock clients in unit tests.
 */
final class ParameterManagerClientFactory {

    private ParameterManagerClientFactory() {}

    /**
     * Creates a new {@link ParameterManagerClient} using the provided options.
     *
     * <p>When {@link GcpParameterManagerProviderOptions#getCredentials()} is non-null, those
     * credentials are used explicitly. Otherwise, the GCP client library falls back to
     * Application Default Credentials (ADC) automatically.
     *
     * @param options the provider options
     * @return a configured {@link ParameterManagerClient}
     * @throws IOException if the client cannot be created
     */
    static ParameterManagerClient create(GcpParameterManagerProviderOptions options) throws IOException {
        ParameterManagerSettings.Builder settingsBuilder = ParameterManagerSettings.newBuilder();
        if (options.getCredentials() != null) {
            settingsBuilder.setCredentialsProvider(FixedCredentialsProvider.create(options.getCredentials()));
        }
        return ParameterManagerClient.create(settingsBuilder.build());
    }
}
