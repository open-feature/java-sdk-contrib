package dev.openfeature.contrib.providers.gcp;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.gax.rpc.NotFoundException;
import com.google.cloud.parametermanager.v1.ParameterManagerClient;
import com.google.cloud.parametermanager.v1.ParameterVersionName;
import com.google.cloud.parametermanager.v1.RenderParameterVersionResponse;
import com.google.protobuf.ByteString;
import dev.openfeature.sdk.FeatureProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("GcpParameterManagerProvider")
@ExtendWith(MockitoExtension.class)
class GcpParameterManagerProviderTest extends AbstractGcpProviderTest {

    @Mock
    private ParameterManagerClient mockClient;

    @Override
    protected FeatureProvider createProvider(GcpProviderOptions options) {
        return new GcpParameterManagerProvider(options, mockClient);
    }

    @Override
    protected FeatureProvider createProvider(GcpProviderOptions options, Object client) {
        return new GcpParameterManagerProvider(options, (ParameterManagerClient) client);
    }

    @Override
    protected void stubFetchSuccess(String value) {
        RenderParameterVersionResponse response = RenderParameterVersionResponse.newBuilder()
                .setRenderedPayload(ByteString.copyFromUtf8(value))
                .build();
        when(mockClient.renderParameterVersion(any(ParameterVersionName.class))).thenReturn(response);
    }

    @Override
    protected void stubFetchNotFound() {
        when(mockClient.renderParameterVersion(any(ParameterVersionName.class))).thenThrow(NotFoundException.class);
    }

    @Override
    protected void stubFetchError(String message) {
        when(mockClient.renderParameterVersion(any(ParameterVersionName.class)))
                .thenThrow(new RuntimeException(message));
    }

    @Override
    protected void verifyFetchCalled(int times) {
        verify(mockClient, times(times)).renderParameterVersion(any(ParameterVersionName.class));
    }

    @Override
    protected void verifyClientClosed(int times) {
        verify(mockClient, times(times)).close();
    }

    @Override
    protected String getProviderName() {
        return GcpParameterManagerProvider.PROVIDER_NAME;
    }

    @Override
    protected GcpProviderOptions.GcpProviderOptionsBuilder newOptionsBuilder() {
        return GcpProviderOptions.builder().projectId("test-project").locationId("europe-west1");
    }
}
