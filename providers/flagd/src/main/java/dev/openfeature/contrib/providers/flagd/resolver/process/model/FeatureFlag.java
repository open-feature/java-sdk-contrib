package dev.openfeature.contrib.providers.flagd.resolver.process.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/** flagd feature flag model. */
@Getter
@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP"},
        justification = "Feature flag comes as a Json configuration, hence they must be parsed and exposed")
@JsonIgnoreProperties(ignoreUnknown = true)
@EqualsAndHashCode
public class FeatureFlag {
    public static final String EMPTY_TARGETING_STRING = "{}";

    private final String state;
    private final String defaultVariant;
    private final Map<String, Object> variants;
    private final String targeting;
    private final Map<String, Object> metadata;

    /** Construct a flagd feature flag. */
    @JsonCreator
    public FeatureFlag(
            @JsonProperty("state") String state,
            @JsonProperty("defaultVariant") String defaultVariant,
            @JsonProperty("variants") Map<String, Object> variants,
            @JsonProperty("targeting") @JsonDeserialize(using = StringSerializer.class) String targeting,
            @JsonProperty("metadata") Map<String, Object> metadata) {
        this.state = state;
        this.defaultVariant = defaultVariant;
        this.variants = variants;
        this.targeting = targeting;
        if (metadata == null) {
            this.metadata = Collections.emptyMap();
        } else {
            this.metadata = metadata;
        }
    }

    /** Construct a flagd feature flag. */
    public FeatureFlag(String state, String defaultVariant, Map<String, Object> variants, String targeting) {
        this.state = state;
        this.defaultVariant = defaultVariant;
        this.variants = variants;
        this.targeting = targeting;
        this.metadata = Collections.emptyMap();
    }

    /** Get targeting rule of the flag. */
    public String getTargeting() {
        return this.targeting == null ? EMPTY_TARGETING_STRING : this.targeting;
    }
}
