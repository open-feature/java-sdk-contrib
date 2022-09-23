package dev.openfeature.contrib.providers.gofeatureflag.bean;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

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


