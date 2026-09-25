package dev.openfeature.contrib.providers.gofeatureflag;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.openfeature.contrib.providers.gofeatureflag.bean.EvaluationType;
import dev.openfeature.contrib.providers.gofeatureflag.exception.InvalidEndpoint;
import dev.openfeature.contrib.providers.gofeatureflag.exception.InvalidExporterMetadata;
import dev.openfeature.contrib.providers.gofeatureflag.exception.InvalidOptions;
import java.util.Map;
import lombok.SneakyThrows;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Provider options validation")
class GoFeatureFlagProviderOptionsTest extends AbstractGoFeatureFlagProviderTest {
    @Test
    void constructor_options_null() {
        assertThrows(InvalidOptions.class, () -> new GoFeatureFlagProvider(null));
    }

    @Test
    void constructor_options_empty() {
        assertThrows(
                InvalidOptions.class,
                () -> new GoFeatureFlagProvider(
                        GoFeatureFlagProviderOptions.builder().build()));
    }

    @SneakyThrows
    @Test
    void constructor_options_empty_endpoint() {
        assertThrows(
                InvalidEndpoint.class,
                () -> new GoFeatureFlagProvider(
                        GoFeatureFlagProviderOptions.builder().endpoint("").build()));
    }

    @SneakyThrows
    @Test
    void constructor_options_only_timeout() {
        assertThrows(
                InvalidEndpoint.class,
                () -> new GoFeatureFlagProvider(
                        GoFeatureFlagProviderOptions.builder().timeout(10000).build()));
    }

    @SneakyThrows
    @Test
    void constructor_options_valid_endpoint() {
        assertDoesNotThrow(() -> new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                .endpoint("http://localhost:1031")
                .build()));
    }

    @DisplayName("Should error if the metadata is not a valid type")
    @SneakyThrows
    @Test
    void shouldErrorIfTheMetadataIsNotAValidType() {
        assertThrows(
                InvalidExporterMetadata.class,
                () -> new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                        .endpoint(baseUrl.toString())
                        .exporterMetadata(Map.of(
                                // object is not a valid metadata
                                "invalid-metadata", goffAPIMock))
                        .evaluationType(EvaluationType.REMOTE)
                        .build()));
    }

    @DisplayName("Should error if invalid flush interval is set")
    @SneakyThrows
    @Test
    void shouldErrorIfInvalidFlushIntervalIsSet() {
        assertThrows(
                InvalidOptions.class,
                () -> new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                        .flushIntervalMs(-1L)
                        .maxPendingEvents(1000)
                        .endpoint(baseUrl.toString())
                        .evaluationType(EvaluationType.IN_PROCESS)
                        .build()));
    }

    @DisplayName("Should error if invalid max pending events is set")
    @SneakyThrows
    @Test
    void shouldErrorIfInvalidMaxPendingEventsIsSet() {
        assertThrows(
                InvalidOptions.class,
                () -> new GoFeatureFlagProvider(GoFeatureFlagProviderOptions.builder()
                        .flushIntervalMs(100L)
                        .maxPendingEvents(-1000)
                        .endpoint(baseUrl.toString())
                        .evaluationType(EvaluationType.IN_PROCESS)
                        .build()));
    }
}
