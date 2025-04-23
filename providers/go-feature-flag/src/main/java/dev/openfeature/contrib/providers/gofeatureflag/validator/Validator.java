package dev.openfeature.contrib.providers.gofeatureflag.validator;

import dev.openfeature.contrib.providers.gofeatureflag.GoFeatureFlagProviderOptions;
import dev.openfeature.contrib.providers.gofeatureflag.exception.InvalidEndpoint;
import dev.openfeature.contrib.providers.gofeatureflag.exception.InvalidExporterMetadata;
import dev.openfeature.contrib.providers.gofeatureflag.exception.InvalidOptions;
import dev.openfeature.contrib.providers.gofeatureflag.hook.DataCollectorHookOptions;
import java.util.List;
import java.util.Map;
import lombok.val;

public class Validator {
    public static void ProviderOptions(GoFeatureFlagProviderOptions options) throws InvalidOptions {
        if (options == null) {
            throw new InvalidOptions("No options provided");
        }

        if (options.getEndpoint() == null || options.getEndpoint().isEmpty()) {
            throw new InvalidEndpoint("endpoint is a mandatory field when initializing the provider");
        }

        if (options.getExporterMetadata() != null) {
            val acceptableExporterMetadataTypes = List.of("String", "Boolean", "Integer", "Double");
            for (Map.Entry<String, Object> entry : options.getExporterMetadata().entrySet()) {
                if (!acceptableExporterMetadataTypes.contains(entry.getValue().getClass().getSimpleName())) {
                    throw new InvalidExporterMetadata(
                            "exporterMetadata can only contain String, Boolean, Integer or Double");
                }
            }
        }
    }

    public static void PublisherOptions(final Long flushIntervalMs, final Integer maxPendingEvents)
            throws InvalidOptions {
        if (flushIntervalMs != null && flushIntervalMs <= 0) {
            throw new InvalidOptions("flushIntervalMs must be larger than 0");
        }
        if (maxPendingEvents != null && maxPendingEvents <= 0) {
            throw new InvalidOptions("maxPendingEvents must be larger than 0");
        }
    }

    public static void DataCollectorHookOptions(final DataCollectorHookOptions options) throws InvalidOptions {
        if (options == null) {
            throw new InvalidOptions("No options provided");
        }
        if (options.getEventsPublisher() == null) {
            throw new InvalidOptions("No events publisher provided");
        }
    }
}
