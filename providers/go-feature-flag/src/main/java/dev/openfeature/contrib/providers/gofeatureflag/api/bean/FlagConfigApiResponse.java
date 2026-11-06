package dev.openfeature.contrib.providers.gofeatureflag.api.bean;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import lombok.Data;

/**
 * Represents the response body for the flag configuration API.
 *
 * <p>Flags are kept as raw JSON: the evaluation engine owns the flag schema, so deserialising it
 * into a typed model here would silently drop any field a newer engine adds and hand a truncated
 * flag to evaluation.</p>
 */
@Data
public class FlagConfigApiResponse {
    @JsonProperty("flags")
    private Map<String, JsonNode> flags;

    @JsonProperty("evaluationContextEnrichment")
    private Map<String, Object> evaluationContextEnrichment;

    FlagConfigApiResponse() {
        // Default constructor
    }
}
