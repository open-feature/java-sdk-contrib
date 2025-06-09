package dev.openfeature.contrib.providers.gofeatureflag.bean;

import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * FlagBase is a class that represents the base structure of a feature flag for GO Feature Flag.
 */
@Data
public abstract class FlagBase {
    private Map<String, Object> variations;
    private List<Rule> targeting;
    private String bucketingKey;
    private Rule defaultRule;
    private ExperimentationRollout experimentation;
    private Boolean trackEvents;
    private Boolean disable;
    private String version;
    private Map<String, Object> metadata;
}
