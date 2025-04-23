package dev.openfeature.contrib.providers.v2.gofeatureflag.bean;

import lombok.Data;
import java.util.Date;

@Data
public class ProgressiveRolloutStep {
    private String variation;
    private Float percentage;
    private Date date;
}
