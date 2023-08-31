package dev.openfeature.contrib.providers.flagd.resolver.process.storage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Getter;

import java.util.Map;

@Getter
public class FlagModel {
    private final String state;
    private final String defaultVariant;
    private final Map<String, Object> variants;
    private final String targeting;

    @JsonCreator
    public FlagModel(@JsonProperty("state") String state,
                     @JsonProperty("defaultVariant") String defaultVariant,
                     @JsonProperty("variants") Map<String, Object> variants,
                     @JsonProperty("targeting") @JsonDeserialize(using = StringParser.class) String targeting) {
        this.state = state;
        this.defaultVariant = defaultVariant;
        this.variants = variants;
        this.targeting = targeting;
    }


    public String getTargeting() {
        return this.targeting == null ? "" : this.targeting;
    }
}
