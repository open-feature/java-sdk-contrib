package dev.openfeature.contrib.providers.gofeatureflag.bean;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * GoFeatureFlagResponse is the response returned by the relay proxy.
 */
@Getter
@Setter
@ToString
public class GoFeatureFlagResponse {
    private boolean trackEvents;
    private String variationType;
    private boolean failed;
    private String version;
    private String reason;
    private String errorCode;
    private Object value;
}


