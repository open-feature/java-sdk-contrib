package dev.openfeature.contrib.providers.gofeatureflag.api.bean;

import java.util.Map;
import lombok.Builder;
import lombok.Data;

/**
 * Represents the request body for the OFREP API request.
 */
@Data
@Builder
public class OfrepRequest {
    private Map<String, Object> context;
}
