package dev.openfeature.contrib.providers.gcp;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.gax.rpc.NotFoundException;
import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretPayload;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import com.google.protobuf.ByteString;
import dev.openfeature.sdk.FeatureProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("GcpSecretManagerProvider")
@ExtendWith(MockitoExtension.class)
class GcpSecretManagerProviderTest extends AbstractGcpProviderTest {

    @Mock
    private SecretManagerServiceClient mockClient;

    @Override
    protected FeatureProvider createProvider(GcpProviderOptions options) {
        return new GcpSecretManagerProvider(options, mockClient);
    }

    @Override
    protected FeatureProvider createProvider(GcpProviderOptions options, Object client) {
        return new GcpSecretManagerProvider(options, (SecretManagerServiceClient) client);
    }

    @Override
    protected void stubFetchSuccess(String value) {
        AccessSecretVersionResponse response = AccessSecretVersionResponse.newBuilder()
                .setPayload(SecretPayload.newBuilder()
                        .setData(ByteString.copyFromUtf8(value))
                        .build())
                .build();
        when(mockClient.accessSecretVersion(any(SecretVersionName.class))).thenReturn(response);
    }

    @Override
    protected void stubFetchNotFound() {
        when(mockClient.accessSecretVersion(any(SecretVersionName.class))).thenThrow(NotFoundException.class);
    }

    @Override
    protected void stubFetchError(String message) {
        when(mockClient.accessSecretVersion(any(SecretVersionName.class))).thenThrow(new RuntimeException(message));
    }

    @Override
    protected void verifyFetchCalled(int times) {
        verify(mockClient, times(times)).accessSecretVersion(any(SecretVersionName.class));
    }

    @Override
    protected void verifyClientClosed(int times) {
        verify(mockClient, times(times)).close();
    }

    @Override
    protected String getProviderName() {
        return GcpSecretManagerProvider.PROVIDER_NAME;
    }

    @Override
    protected GcpProviderOptions.GcpProviderOptionsBuilder newOptionsBuilder() {
        return GcpProviderOptions.builder().projectId("test-project");
    }
}
