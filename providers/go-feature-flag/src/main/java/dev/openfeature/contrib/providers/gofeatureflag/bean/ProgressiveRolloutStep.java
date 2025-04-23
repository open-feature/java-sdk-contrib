package dev.openfeature.contrib.providers.gofeatureflag.bean;

import java.util.Date;
import lombok.Data;

@Data
public class ProgressiveRolloutStep {
    private String variation;
    private Float percentage;
    private Date date;
}
