package dev.openfeature.contrib.providers.v2.gofeatureflag.bean;

import java.util.Date;
import java.util.Map;
import lombok.Data;

@Data
public class FlagConfigResponse {
    private Map<String, Flag> flags;
    private Map<String, Object> evaluationContextEnrichment;
    private Date lastUpdated;
    private String etag;
}
