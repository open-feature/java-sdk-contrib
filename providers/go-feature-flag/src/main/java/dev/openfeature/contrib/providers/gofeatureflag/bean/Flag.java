package dev.openfeature.contrib.providers.gofeatureflag.bean;

import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Flag is a class that represents a feature flag for GO Feature Flag.
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class Flag extends FlagBase {
    private List<ScheduledStep> scheduledRollout;
}
