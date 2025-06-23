package dev.openfeature.contrib.providers.ofrep.internal;

import com.google.common.collect.ImmutableMap;
import lombok.Value;

/**
 * Represents the request body for the OFREP API request.
 */
@Value
public class OfrepRequest {
    ImmutableMap<String, Object> context;
}
