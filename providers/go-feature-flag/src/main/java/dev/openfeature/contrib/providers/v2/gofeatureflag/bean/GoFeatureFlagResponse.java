package dev.openfeature.contrib.providers.v2.gofeatureflag.bean;

import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Data
@NoArgsConstructor
@Setter
public class GoFeatureFlagResponse {
    private String variationType;
    private boolean failed;
    private String version;
    private String reason;
    private String errorCode;
    private String errorDetails;
    private Object value;
    private Boolean cacheable;
    private Map<String, Object> metadata;
}
