package dev.openfeature.contrib.providers.gofeatureflag.hook.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

/**
 * Event data.
 */
@Builder
@Data
public class Event {
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
