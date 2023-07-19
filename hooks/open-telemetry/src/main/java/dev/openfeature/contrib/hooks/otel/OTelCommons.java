package dev.openfeature.contrib.hooks.otel;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;

class OTelCommons {
    // Define semantic conventions
    // Refer - https://opentelemetry.io/docs/specs/otel/logs/semantic_conventions/feature-flags/
    static final String EVENT_NAME = "feature_flag";

    static final AttributeKey<String> flagKeyAttributeKey = SemanticAttributes.FEATURE_FLAG_KEY;
    static final AttributeKey<String> providerNameAttributeKey = SemanticAttributes.FEATURE_FLAG_PROVIDER_NAME;
    static final AttributeKey<String> variantAttributeKey = SemanticAttributes.FEATURE_FLAG_VARIANT;

    // Define non convention attribute keys
    static final String REASON_KEY = "reason";
    static final String ERROR_KEY = "exception";
}
