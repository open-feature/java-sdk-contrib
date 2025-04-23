package dev.openfeature.contrib.providers.v2.gofeatureflag.bean;

import lombok.Data;
import java.util.Date;

@Data
public class ExperimentationRollout {
    private Date start;
    private Date end;
}
