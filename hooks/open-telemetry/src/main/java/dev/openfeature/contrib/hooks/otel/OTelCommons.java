package dev.openfeature.contrib.hooks.otel;

import io.opentelemetry.api.common.AttributeKey;

import static io.opentelemetry.semconv.incubating.FeatureFlagIncubatingAttributes.FEATURE_FLAG_KEY;
import static io.opentelemetry.semconv.incubating.FeatureFlagIncubatingAttributes.FEATURE_FLAG_PROVIDER_NAME;
import static io.opentelemetry.semconv.incubating.FeatureFlagIncubatingAttributes.FEATURE_FLAG_VARIANT;

class OTelCommons {
    // Define semantic conventions
    // Refer - https://opentelemetry.io/docs/specs/otel/logs/semantic_conventions/feature-flags/
    static final String EVENT_NAME = "feature_flag";

    static final AttributeKey<String> flagKeyAttributeKey = FEATURE_FLAG_KEY;
    static final AttributeKey<String> providerNameAttributeKey = FEATURE_FLAG_PROVIDER_NAME;
    static final AttributeKey<String> variantAttributeKey = FEATURE_FLAG_VARIANT;

    // Define non convention attribute keys
    static final String REASON_KEY = "reason";
}
