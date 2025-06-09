package dev.openfeature.contrib.providers.gofeatureflag.bean;

import java.util.Date;
import lombok.Data;

/**
 * This class represents the rollout of an experimentation.
 */
@Data
public class ExperimentationRollout {
    private Date start;
    private Date end;
}
