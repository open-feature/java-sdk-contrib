package dev.openfeature.contrib.providers.gofeatureflag.bean;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * GoFeatureFlagRequest is the request send to the relay proxy.
 *
 * @param <T> The default value we are using.
 */
@Getter
@AllArgsConstructor
public class GoFeatureFlagRequest<T> {
    private GoFeatureFlagUser user;
    private T defaultValue;
}
