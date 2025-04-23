package dev.openfeature.contrib.providers.v2.gofeatureflag.bean;

import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class Flag {
    private Map<String, Object> variations;
    private List<Rule> targeting;
    private String bucketingKey;
    private Rule defaultRule;
    private ExperimentationRollout experimentation;
    private Boolean trackEvents;
    private Boolean disable;
    private String version;
    private Map<String, Object> metadata;
    // TODO: scheduledRollout
}
