package dev.openfeature.contrib.hooks.otel;

import io.opentelemetry.api.common.AttributeKey;

class OTelCommons {
    // Define semantic conventions
    // Refer - https://opentelemetry.io/docs/specs/otel/logs/semantic_conventions/feature-flags/
    static final String EVENT_NAME = "feature_flag";
    static final AttributeKey<String> flagKeyAttributeKey = AttributeKey.stringKey(EVENT_NAME + ".flag_key");
    static final AttributeKey<String> providerNameAttributeKey = AttributeKey.stringKey(EVENT_NAME + ".provider_name");
    static final AttributeKey<String> variantAttributeKey = AttributeKey.stringKey(EVENT_NAME + ".variant");

    // Define non convention attribute keys
    static final String REASON_KEY = "reason";
    static final String ERROR_KEY = "exception";
}
