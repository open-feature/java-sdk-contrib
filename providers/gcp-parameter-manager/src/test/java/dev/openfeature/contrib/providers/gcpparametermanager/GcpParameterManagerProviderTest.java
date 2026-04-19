package dev.openfeature.contrib.providers.gcpparametermanager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.gax.rpc.NotFoundException;
import com.google.cloud.parametermanager.v1.ParameterManagerClient;
import com.google.cloud.parametermanager.v1.ParameterVersionName;
import com.google.cloud.parametermanager.v1.RenderParameterVersionResponse;
import com.google.protobuf.ByteString;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Reason;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.FlagNotFoundError;
import dev.openfeature.sdk.exceptions.GeneralError;
import dev.openfeature.sdk.exceptions.ParseError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("GcpParameterManagerProvider")
@ExtendWith(MockitoExtension.class)
class GcpParameterManagerProviderTest {

    @Mock
    private ParameterManagerClient mockClient;

    private GcpParameterManagerProviderOptions options;
    private GcpParameterManagerProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        options = GcpParameterManagerProviderOptions.builder()
                .projectId("test-project")
                .build();
        provider = new GcpParameterManagerProvider(options, mockClient);
        provider.initialize(new ImmutableContext());
    }

    private void stubParameter(String key, String value) {
        RenderParameterVersionResponse response = RenderParameterVersionResponse.newBuilder()
                .setRenderedPayload(ByteString.copyFromUtf8(value))
                .build();
        when(mockClient.renderParameterVersion(any(ParameterVersionName.class))).thenReturn(response);
    }

    private void stubParameterNotFound() {
        when(mockClient.renderParameterVersion(any(ParameterVersionName.class))).thenThrow(NotFoundException.class);
    }

    private void stubParameterError(String message) {
        when(mockClient.renderParameterVersion(any(ParameterVersionName.class)))
                .thenThrow(new RuntimeException(message));
    }

    @Nested
    @DisplayName("Metadata")
    class MetadataTests {
        @Test
        @DisplayName("returns the correct provider name")
        void providerName() {
            assertThat(provider.getMetadata().getName()).isEqualTo(GcpParameterManagerProvider.PROVIDER_NAME);
        }
    }

    @Nested
    @DisplayName("Initialization")
    class InitializationTests {
        @Test
        @DisplayName("throws IllegalArgumentException when projectId is blank")
        void blankProjectIdThrows() {
            GcpParameterManagerProviderOptions badOpts =
                    GcpParameterManagerProviderOptions.builder().projectId("").build();
            GcpParameterManagerProvider badProvider = new GcpParameterManagerProvider(badOpts, mockClient);
            assertThatThrownBy(() -> badProvider.initialize(new ImmutableContext()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws IllegalArgumentException when projectId is null")
        void nullProjectIdThrows() {
            GcpParameterManagerProviderOptions badOpts =
                    GcpParameterManagerProviderOptions.builder().build();
            GcpParameterManagerProvider badProvider = new GcpParameterManagerProvider(badOpts, mockClient);
            assertThatThrownBy(() -> badProvider.initialize(new ImmutableContext()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Boolean evaluation")
    class BooleanEvaluation {
        @Test
        @DisplayName("returns true for parameter value 'true'")
        void trueValue() {
            stubParameter("bool-flag", "true");
            ProviderEvaluation<Boolean> result =
                    provider.getBooleanEvaluation("bool-flag", false, new ImmutableContext());
            assertThat(result.getValue()).isTrue();
            assertThat(result.getReason()).isEqualTo(Reason.CACHED.toString());
        }

        @Test
        @DisplayName("returns false for parameter value 'false'")
        void falseValue() {
            stubParameter("bool-flag", "false");
            ProviderEvaluation<Boolean> result =
                    provider.getBooleanEvaluation("bool-flag", true, new ImmutableContext());
            assertThat(result.getValue()).isFalse();
        }

        @Test
        @DisplayName("throws ParseError for malformed boolean value")
        void malformedBooleanThrows() {
            stubParameter("bool-flag", "not-a-bool");
            assertThatThrownBy(() -> provider.getBooleanEvaluation("bool-flag", false, new ImmutableContext()))
                    .isInstanceOf(ParseError.class);
        }
    }

    @Nested
    @DisplayName("String evaluation")
    class StringEvaluation {
        @Test
        @DisplayName("returns string value as-is")
        void stringValue() {
            stubParameter("str-flag", "dark-mode");
            ProviderEvaluation<String> result =
                    provider.getStringEvaluation("str-flag", "light-mode", new ImmutableContext());
            assertThat(result.getValue()).isEqualTo("dark-mode");
        }
    }

    @Nested
    @DisplayName("Integer evaluation")
    class IntegerEvaluation {
        @Test
        @DisplayName("parses numeric string to Integer")
        void integerValue() {
            stubParameter("int-flag", "42");
            ProviderEvaluation<Integer> result = provider.getIntegerEvaluation("int-flag", 0, new ImmutableContext());
            assertThat(result.getValue()).isEqualTo(42);
        }

        @Test
        @DisplayName("throws ParseError for non-numeric value")
        void nonNumericThrows() {
            stubParameter("int-flag", "abc");
            assertThatThrownBy(() -> provider.getIntegerEvaluation("int-flag", 0, new ImmutableContext()))
                    .isInstanceOf(ParseError.class);
        }
    }

    @Nested
    @DisplayName("Double evaluation")
    class DoubleEvaluation {
        @Test
        @DisplayName("parses numeric string to Double")
        void doubleValue() {
            stubParameter("double-flag", "3.14");
            ProviderEvaluation<Double> result =
                    provider.getDoubleEvaluation("double-flag", 0.0, new ImmutableContext());
            assertThat(result.getValue()).isEqualTo(3.14);
        }
    }

    @Nested
    @DisplayName("Object evaluation")
    class ObjectEvaluation {
        @Test
        @DisplayName("parses JSON string to Value/Structure")
        void jsonValue() {
            stubParameter("obj-flag", "{\"color\":\"blue\",\"count\":3}");
            ProviderEvaluation<Value> result =
                    provider.getObjectEvaluation("obj-flag", new Value(), new ImmutableContext());
            assertThat(result.getValue().asStructure()).isNotNull();
            assertThat(result.getValue().asStructure().getValue("color").asString())
                    .isEqualTo("blue");
        }
    }

    @Nested
    @DisplayName("Error handling")
    class ErrorHandling {
        @Test
        @DisplayName("throws FlagNotFoundError when parameter does not exist")
        void flagNotFound() {
            stubParameterNotFound();
            assertThatThrownBy(() -> provider.getBooleanEvaluation("missing-flag", false, new ImmutableContext()))
                    .isInstanceOf(FlagNotFoundError.class);
        }

        @Test
        @DisplayName("throws GeneralError on unexpected GCP API exception")
        void gcpApiError() {
            stubParameterError("Connection refused");
            assertThatThrownBy(() -> provider.getBooleanEvaluation("flag", false, new ImmutableContext()))
                    .isInstanceOf(GeneralError.class);
        }
    }

    @Nested
    @DisplayName("Caching")
    class CachingTests {
        @Test
        @DisplayName("cache hit: GCP client called only once for two consecutive evaluations")
        void cacheHit() {
            stubParameter("cached-flag", "true");
            provider.getBooleanEvaluation("cached-flag", false, new ImmutableContext());
            provider.getBooleanEvaluation("cached-flag", false, new ImmutableContext());
            verify(mockClient, times(1)).renderParameterVersion(any(ParameterVersionName.class));
        }
    }

    @Nested
    @DisplayName("Parameter name prefix")
    class PrefixTests {
        @Test
        @DisplayName("prefix is prepended to the flag key when building parameter name")
        void prefixApplied() {
            GcpParameterManagerProviderOptions prefixedOpts = GcpParameterManagerProviderOptions.builder()
                    .projectId("test-project")
                    .parameterNamePrefix("ff-")
                    .build();
            stubParameter("ff-my-flag", "true");
            GcpParameterManagerProvider prefixedProvider = new GcpParameterManagerProvider(prefixedOpts, mockClient);
            try {
                prefixedProvider.initialize(new ImmutableContext());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            ProviderEvaluation<Boolean> result =
                    prefixedProvider.getBooleanEvaluation("my-flag", false, new ImmutableContext());
            assertThat(result.getValue()).isTrue();
        }
    }

    @Nested
    @DisplayName("Lifecycle")
    class LifecycleTests {
        @Test
        @DisplayName("shutdown() closes the GCP client")
        void shutdownClosesClient() {
            provider.shutdown();
            verify(mockClient, times(1)).close();
        }
    }
}
