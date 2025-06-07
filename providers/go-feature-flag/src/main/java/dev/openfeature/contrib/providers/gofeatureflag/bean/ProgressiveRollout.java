package dev.openfeature.contrib.providers.gofeatureflag.bean;

import lombok.Data;

/**
 * ProgressiveRollout is a class that represents the progressive rollout of a feature flag.
 */
@Data
public class ProgressiveRollout {
    private ProgressiveRolloutStep initial;
    private ProgressiveRolloutStep end;
}
