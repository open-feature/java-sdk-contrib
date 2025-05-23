package dev.openfeature.contrib.providers.gofeatureflag.api.bean;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.openfeature.contrib.providers.gofeatureflag.bean.Flag;
import java.util.Map;
import lombok.Data;

/**
 * Represents the response body for the flag configuration API.
 */
@Data
public class FlagConfigApiResponse {
    @JsonProperty("flags")
    private Map<String, Flag> flags;

    @JsonProperty("evaluationContextEnrichment")
    private Map<String, Object> evaluationContextEnrichment;

    FlagConfigApiResponse() {
        // Default constructor
    }

}
