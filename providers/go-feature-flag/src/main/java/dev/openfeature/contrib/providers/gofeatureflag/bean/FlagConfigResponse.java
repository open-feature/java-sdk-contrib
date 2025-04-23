package dev.openfeature.contrib.providers.gofeatureflag.bean;

import java.util.Date;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FlagConfigResponse {
    private Map<String, Flag> flags;
    private Map<String, Object> evaluationContextEnrichment;
    private String etag;
    private Date lastUpdated;
}
