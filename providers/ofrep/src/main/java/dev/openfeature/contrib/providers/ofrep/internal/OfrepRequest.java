package dev.openfeature.contrib.providers.ofrep.internal;

import com.google.common.collect.ImmutableMap;
import lombok.Data;

/**
 * Represents the request body for the OFREP API request.
 */
@Data
public class OfrepRequest {
    private final ImmutableMap<String, Object> context;
}
