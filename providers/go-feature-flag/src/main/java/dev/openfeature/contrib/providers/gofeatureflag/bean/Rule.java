package dev.openfeature.contrib.providers.gofeatureflag.bean;

import java.util.Map;
import lombok.Data;

/**
 * This class represents a rule in the GO Feature Flag system.
 */
@Data
public class Rule {
    private String name;
    private String query;
    private String variation;
    private Map<String, Double> percentage;
    private Boolean disable;
    private ProgressiveRollout progressiveRollout;
}
