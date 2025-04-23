package dev.openfeature.contrib.providers.v2.gofeatureflag.bean;

import lombok.Data;
import java.util.Map;

@Data
public class Rule {
    private String name;
    private String query;
    private String variation;
    private Map<String, Long> percentage;
    private Boolean disable;
    private ProgressiveRollout progressiveRollout;
}
