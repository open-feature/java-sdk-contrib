package dev.openfeature.contrib.providers.gofeatureflag.bean;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * GoFeatureFlagResponse is the response returned by the relay proxy.
 *
 * @param <T> The type of the response.
 */
@Getter
@Setter
@ToString
public class GoFeatureFlagResponse<T> {
    private boolean trackEvents;
    private String variationType;
    private boolean failed;
    private String version;
    private String reason;
    private String errorCode;
    private T value;
}


