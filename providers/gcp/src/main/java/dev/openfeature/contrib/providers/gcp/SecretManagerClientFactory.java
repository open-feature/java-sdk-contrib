package dev.openfeature.contrib.providers.gcp;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretManagerServiceSettings;
import java.io.IOException;

/**
 * Factory for creating a {@link SecretManagerServiceClient}, separated to allow injection
 * of mock clients in unit tests.
 */
final class SecretManagerClientFactory {

    private SecretManagerClientFactory() {}

    /**
     * Creates a new {@link SecretManagerServiceClient} using the provided options.
     *
     * <p>When {@link GcpSecretManagerProviderOptions#getCredentials()} is non-null, those
     * credentials are used explicitly. Otherwise, the GCP client library falls back to
     * Application Default Credentials (ADC) automatically.
     *
     * @param options the provider options
     * @return a configured {@link SecretManagerServiceClient}
     * @throws IOException if the client cannot be created
     */
    static SecretManagerServiceClient create(GcpSecretManagerProviderOptions options) throws IOException {
        SecretManagerServiceSettings.Builder settingsBuilder = SecretManagerServiceSettings.newBuilder();
        if (options.getCredentials() != null) {
            settingsBuilder.setCredentialsProvider(FixedCredentialsProvider.create(options.getCredentials()));
        }
        return SecretManagerServiceClient.create(settingsBuilder.build());
    }
}
