package dev.openfeature.contrib.providers.v2.gofeatureflag.bean;

import lombok.Data;

@Data
public class ProgressiveRollout {
    private ProgressiveRolloutStep initial;
    private ProgressiveRolloutStep end;
}
