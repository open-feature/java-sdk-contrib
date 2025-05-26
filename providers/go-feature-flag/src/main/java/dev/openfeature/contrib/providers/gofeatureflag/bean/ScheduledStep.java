package dev.openfeature.contrib.providers.gofeatureflag.bean;

import java.util.Date;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * ScheduledStep is a class that represents a scheduled step in the rollout of a feature flag.
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class ScheduledStep extends FlagBase {
    private Date date;
}
