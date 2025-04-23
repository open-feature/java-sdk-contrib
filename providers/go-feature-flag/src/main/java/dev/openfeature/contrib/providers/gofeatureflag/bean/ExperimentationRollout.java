package dev.openfeature.contrib.providers.gofeatureflag.bean;

import java.util.Date;
import lombok.Data;

@Data
public class ExperimentationRollout {
    private Date start;
    private Date end;
}
