package dev.openfeature.contrib.providers.gofeatureflag.bean;

import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GoFeatureFlagResponse {
    private String variationType;
    private boolean failed;
    private String version;
    private String reason;
    private String errorCode;
    private String errorDetails;
    private Object value;
    private boolean cacheable;
    private boolean trackEvents;
    private Map<String, Object> metadata;
}
