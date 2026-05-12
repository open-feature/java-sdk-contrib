package dev.openfeature.contrib.providers.gcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

@DisplayName("GcpSecretManagerProvider")
@ExtendWith(MockitoExtension.class)
class GcpSecretManagerProviderTest {

    @Mock
    private SecretManagerServiceClient mockClient;

    private GcpProviderOptions options;
    private GcpSecretManagerProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        options = GcpProviderOptions.builder().projectId("test-project").build();
        provider = new GcpSecretManagerProvider(options, mockClient);
        provider.initialize(new ImmutableContext());
    }

    private void stubSecret(String value) {
        AccessSecretVersionResponse response = AccessSecretVersionResponse.newBuilder()
            .setPayload(SecretPayload.newBuilder().setData(ByteString.copyFromUtf8(value)).build())
            .build();
        when(mockClient.accessSecretVersion(any(SecretVersionName.class))).thenReturn(response);
    }

    private void stubSecretNotFound() {
        when(mockClient.accessSecretVersion(any(SecretVersionName.class))).thenThrow(NotFoundException.class);
    }

    private void stubSecretError(String message) {
        when(mockClient.accessSecretVersion(any(SecretVersionName.class))).thenThrow(new RuntimeException(message));
    }

    @Nested
    @DisplayName("Metadata")
    class MetadataTests {

        @Test
        @DisplayName("returns the correct provider name")
        void providerName() {
            assertThat(provider.getMetadata().getName()).isEqualTo(GcpSecretManagerProvider.PROVIDER_NAME);
        }
    }

    @Nested
    @DisplayName("Initialization")
    class InitializationTests {

        @Test
        @DisplayName("throws IllegalArgumentException when projectId is blank")
        void blankProjectIdThrows() {
            GcpProviderOptions badOpts = GcpProviderOptions.builder().projectId("").build();
            GcpSecretManagerProvider badProvider = new GcpSecretManagerProvider(badOpts, mockClient);
            assertThatThrownBy(() -> badProvider.initialize(new ImmutableContext())).isInstanceOf(
                IllegalArgumentException.class
            );
        }

        @Test
        @DisplayName("throws IllegalArgumentException when projectId is null")
        void nullProjectIdThrows() {
            GcpProviderOptions badOpts = GcpProviderOptions.builder().build();
            GcpSecretManagerProvider badProvider = new GcpSecretManagerProvider(badOpts, mockClient);
            assertThatThrownBy(() -> badProvider.initialize(new ImmutableContext())).isInstanceOf(
                IllegalArgumentException.class
            );
        }
    }

    @Nested
    @DisplayName("Boolean evaluation")
    class BooleanEvaluation {

        @Test
        @DisplayName("returns true for secret value 'true'")
        void trueValue() {
            stubSecret("true");
            ProviderEvaluation<Boolean> result = provider.getBooleanEvaluation(
                "bool-flag",
                false,
                new ImmutableContext()
            );
            assertThat(result.getValue()).isTrue();
            assertThat(result.getReason()).isEqualTo(Reason.STATIC.toString());
        }

        @Test
        @DisplayName("returns false for secret value 'false'")
        void falseValue() {
            stubSecret("false");
            ProviderEvaluation<Boolean> result = provider.getBooleanEvaluation(
                "bool-flag",
                true,
                new ImmutableContext()
            );
            assertThat(result.getValue()).isFalse();
        }

        @Test
        @DisplayName("throws ParseError for malformed boolean value")
        void malformedBooleanThrows() {
            stubSecret("not-a-bool");
            assertThatThrownBy(() ->
                provider.getBooleanEvaluation("bool-flag", false, new ImmutableContext())
            ).isInstanceOf(ParseError.class);
        }
    }

    @Nested
    @DisplayName("String evaluation")
    class StringEvaluation {

        @Test
        @DisplayName("returns string value as-is")
        void stringValue() {
            stubSecret("dark-mode");
            ProviderEvaluation<String> result = provider.getStringEvaluation(
                "str-flag",
                "light-mode",
                new ImmutableContext()
            );
            assertThat(result.getValue()).isEqualTo("dark-mode");
        }
    }

    @Nested
    @DisplayName("Integer evaluation")
    class IntegerEvaluation {

        @Test
        @DisplayName("parses numeric string to Integer")
        void integerValue() {
            stubSecret("42");
            ProviderEvaluation<Integer> result = provider.getIntegerEvaluation("int-flag", 0, new ImmutableContext());
            assertThat(result.getValue()).isEqualTo(42);
        }

        @Test
        @DisplayName("throws ParseError for non-numeric value")
        void nonNumericThrows() {
            stubSecret("abc");
            assertThatThrownBy(() -> provider.getIntegerEvaluation("int-flag", 0, new ImmutableContext())).isInstanceOf(
                ParseError.class
            );
        }
    }

    @Nested
    @DisplayName("Double evaluation")
    class DoubleEvaluation {

        @Test
        @DisplayName("parses numeric string to Double")
        void doubleValue() {
            stubSecret("3.14");
            ProviderEvaluation<Double> result = provider.getDoubleEvaluation(
                "double-flag",
                0.0,
                new ImmutableContext()
            );
            assertThat(result.getValue()).isEqualTo(3.14);
        }
    }

    @Nested
    @DisplayName("Object evaluation")
    class ObjectEvaluation {

        @Test
        @DisplayName("parses JSON string to Value/Structure")
        void jsonValue() {
            stubSecret("{\"color\":\"blue\",\"count\":3}");
            ProviderEvaluation<Value> result = provider.getObjectEvaluation(
                "obj-flag",
                new Value(),
                new ImmutableContext()
            );
            assertThat(result.getValue().asStructure()).isNotNull();
            assertThat(result.getValue().asStructure().getValue("color").asString()).isEqualTo("blue");
        }
    }

    @Nested
    @DisplayName("Error handling")
    class ErrorHandling {

        @Test
        @DisplayName("throws FlagNotFoundError when secret does not exist")
        void flagNotFound() {
            stubSecretNotFound();
            assertThatThrownBy(() ->
                provider.getBooleanEvaluation("missing-flag", false, new ImmutableContext())
            ).isInstanceOf(FlagNotFoundError.class);
        }

        @Test
        @DisplayName("throws GeneralError on unexpected GCP API exception")
        void gcpApiError() {
            stubSecretError("Connection refused");
            assertThatThrownBy(() -> provider.getBooleanEvaluation("flag", false, new ImmutableContext())).isInstanceOf(
                GeneralError.class
            );
        }
    }

    @Nested
    @DisplayName("Caching")
    class CachingTests {

        @Test
        @DisplayName("cache hit: GCP client called only once for two consecutive evaluations")
        void cacheHit() {
            stubSecret("true");
            provider.getBooleanEvaluation("cached-flag", false, new ImmutableContext());
            provider.getBooleanEvaluation("cached-flag", false, new ImmutableContext());
            verify(mockClient, times(1)).accessSecretVersion(any(SecretVersionName.class));
        }
    }

    @Nested
    @DisplayName("Secret name prefix")
    class PrefixTests {

        @Test
        @DisplayName("prefix is prepended to the flag key when building secret name")
        void prefixApplied() {
            GcpProviderOptions prefixedOpts = GcpProviderOptions.builder()
                .projectId("test-project")
                .namePrefix("ff-")
                .build();
            stubSecret("true");
            GcpSecretManagerProvider prefixedProvider = new GcpSecretManagerProvider(prefixedOpts, mockClient);
            try {
                prefixedProvider.initialize(new ImmutableContext());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            ProviderEvaluation<Boolean> result = prefixedProvider.getBooleanEvaluation(
                "my-flag",
                false,
                new ImmutableContext()
            );
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
