package dev.openfeature.contrib.providers.gofeatureflag.bean;

import java.util.Date;
import lombok.Data;

/**
 * ProgressiveRolloutStep is a class that represents a step in the progressive rollout of a feature flag.
 */
@Data
public class ProgressiveRolloutStep {
    private String variation;
    private Float percentage;
    private Date date;
}
