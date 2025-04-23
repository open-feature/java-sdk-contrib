package dev.openfeature.contrib.providers.gofeatureflag.bean;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class FeatureEvent implements IEvent {
    private String contextKind;
    private Long creationDate;

    @JsonProperty("default")
    private Object defaultValue;

    private String key;
    private String kind;
    private String userKey;
    private Object value;
    private String variation;
    private String version;
}
