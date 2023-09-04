package dev.openfeature.contrib.providers.flagd.resolver.process.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Getter;

import java.util.Map;

/**
 * flagd feature flag model.
 */
@Getter
public class FeatureFlag {
    private final String state;
    private final String defaultVariant;
    private final Map<String, Object> variants;
    private final String targeting;

    /**
     * Construct a flagd feature flag.
     */
    @JsonCreator
    public FeatureFlag(@JsonProperty("state") String state,
                       @JsonProperty("defaultVariant") String defaultVariant,
                       @JsonProperty("variants") Map<String, Object> variants,
                       @JsonProperty("targeting") @JsonDeserialize(using = StringSerializer.class) String targeting) {
        this.state = state;
        this.defaultVariant = defaultVariant;
        this.variants = variants;
        this.targeting = targeting;
    }

    /**
     * Get targeting rule of the flag.
     */
    public String getTargeting() {
        return this.targeting == null ? "{}" : this.targeting;
    }
}
