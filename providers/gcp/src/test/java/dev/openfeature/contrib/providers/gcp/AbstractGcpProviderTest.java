package dev.openfeature.contrib.providers.gcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.openfeature.sdk.FeatureProvider;
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

@DisplayName("GCP provider shared behavior")
abstract class AbstractGcpProviderTest {

    protected GcpProviderOptions options;
    protected FeatureProvider provider;

    protected abstract FeatureProvider createProvider(GcpProviderOptions options);

    protected abstract FeatureProvider createProvider(GcpProviderOptions options, Object client);

    protected abstract void stubFetchSuccess(String value);

    protected abstract void stubFetchNotFound();

    protected abstract void stubFetchError(String message);

    protected abstract void verifyFetchCalled(int times);

    protected abstract void verifyClientClosed(int times);

    protected abstract String getProviderName();

    protected abstract GcpProviderOptions.GcpProviderOptionsBuilder newOptionsBuilder();

    @BeforeEach
    void setUp() throws Exception {
        options = newOptionsBuilder().build();
        provider = createProvider(options);
        provider.initialize(new ImmutableContext());
    }

    @Nested
    @DisplayName("Metadata")
    class MetadataTests {

        @Test
        @DisplayName("returns the correct provider name")
        void providerName() {
            assertThat(provider.getMetadata().getName()).isEqualTo(getProviderName());
        }
    }

    @Nested
    @DisplayName("Initialization")
    class InitializationTests {

        @Test
        @DisplayName("throws IllegalArgumentException when projectId is blank")
        void blankProjectIdThrows() {
            GcpProviderOptions badOpts = newOptionsBuilder().projectId("").build();
            FeatureProvider badProvider = createProvider(badOpts);
            assertThatThrownBy(() -> badProvider.initialize(new ImmutableContext()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws IllegalArgumentException when projectId is null")
        void nullProjectIdThrows() {
            GcpProviderOptions badOpts = newOptionsBuilder().projectId(null).build();
            FeatureProvider badProvider = createProvider(badOpts);
            assertThatThrownBy(() -> badProvider.initialize(new ImmutableContext()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws GeneralError when initialized twice")
        void doubleInitializationThrows() {
            GcpProviderOptions opts = newOptionsBuilder().projectId("test-project").build();
            FeatureProvider badProvider = createProvider(opts);

            assertThatCode(() -> badProvider.initialize(new ImmutableContext())).doesNotThrowAnyException();

            assertThatThrownBy(() -> badProvider.initialize(new ImmutableContext()))
                    .isInstanceOf(GeneralError.class);
        }
    }

    @Nested
    @DisplayName("Boolean evaluation")
    class BooleanEvaluation {

        @Test
        @DisplayName("returns true for value 'true'")
        void trueValue() {
            stubFetchSuccess("true");
            ProviderEvaluation<Boolean> result =
                    provider.getBooleanEvaluation("bool-flag", false, new ImmutableContext());
            assertThat(result.getValue()).isTrue();
            assertThat(result.getReason()).isEqualTo(Reason.STATIC.toString());
        }

        @Test
        @DisplayName("returns false for value 'false'")
        void falseValue() {
            stubFetchSuccess("false");
            ProviderEvaluation<Boolean> result =
                    provider.getBooleanEvaluation("bool-flag", true, new ImmutableContext());
            assertThat(result.getValue()).isFalse();
        }

        @Test
        @DisplayName("throws ParseError for malformed boolean value")
        void malformedBooleanThrows() {
            stubFetchSuccess("not-a-bool");
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
            stubFetchSuccess("dark-mode");
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
            stubFetchSuccess("42");
            ProviderEvaluation<Integer> result = provider.getIntegerEvaluation("int-flag", 0, new ImmutableContext());
            assertThat(result.getValue()).isEqualTo(42);
        }

        @Test
        @DisplayName("throws ParseError for non-numeric value")
        void nonNumericThrows() {
            stubFetchSuccess("abc");
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
            stubFetchSuccess("3.14");
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
            stubFetchSuccess("{\"color\":\"blue\",\"count\":3}");
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
        @DisplayName("throws FlagNotFoundError when value does not exist")
        void flagNotFound() {
            stubFetchNotFound();
            assertThatThrownBy(() -> provider.getBooleanEvaluation("missing-flag", false, new ImmutableContext()))
                    .isInstanceOf(FlagNotFoundError.class);
        }

        @Test
        @DisplayName("throws GeneralError on unexpected API exception")
        void apiError() {
            stubFetchError("Connection refused");
            assertThatThrownBy(() -> provider.getBooleanEvaluation("flag", false, new ImmutableContext()))
                    .isInstanceOf(GeneralError.class);
        }
    }

    @Nested
    @DisplayName("Caching")
    class CachingTests {

        @Test
        @DisplayName("cache hit: API called only once for repeated evaluations")
        void cacheHit() {
            stubFetchSuccess("true");
            provider.getBooleanEvaluation("cached-flag", false, new ImmutableContext());
            provider.getBooleanEvaluation("cached-flag", false, new ImmutableContext());
            verifyFetchCalled(1);
        }
    }

    @Nested
    @DisplayName("Name prefix")
    class PrefixTests {

        @Test
        @DisplayName("prefix is prepended to the flag key when building the resource name")
        void prefixApplied() throws Exception {
            GcpProviderOptions prefixedOpts =
                    newOptionsBuilder().namePrefix("ff-").build();
            FeatureProvider prefixedProvider = createProvider(prefixedOpts);
            stubFetchSuccess("true");
            prefixedProvider.initialize(new ImmutableContext());
            ProviderEvaluation<Boolean> result =
                    prefixedProvider.getBooleanEvaluation("my-flag", false, new ImmutableContext());
            assertThat(result.getValue()).isTrue();
        }
    }

    @Nested
    @DisplayName("Lifecycle")
    class LifecycleTests {

        @Test
        @DisplayName("shutdown() closes the client")
        void shutdownClosesClient() {
            provider.shutdown();
            verifyClientClosed(1);
        }
    }
}
