package dev.openfeature.contrib.providers.gofeatureflag.bean;

import lombok.Data;

@Data
public class ProgressiveRollout {
    private ProgressiveRolloutStep initial;
    private ProgressiveRolloutStep end;
}
