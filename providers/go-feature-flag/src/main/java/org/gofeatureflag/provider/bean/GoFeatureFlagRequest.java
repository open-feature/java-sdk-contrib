package org.gofeatureflag.provider.bean;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GoFeatureFlagRequest<T> {
    private GoFeatureFlagUser user;
    private T defaultValue;
}
