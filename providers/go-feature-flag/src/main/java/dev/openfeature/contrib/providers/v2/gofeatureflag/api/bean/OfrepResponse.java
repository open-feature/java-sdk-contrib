package dev.openfeature.contrib.providers.v2.gofeatureflag.api.bean;

import dev.openfeature.contrib.providers.v2.gofeatureflag.bean.GoFeatureFlagResponse;
import java.util.Map;
import lombok.Data;
import lombok.val;

@Data
public class OfrepResponse {
    private Object value;
    private String key;
    private String variant;
    private String reason;
    private boolean cacheable;
    private Map<String, Object> metadata;

    private String errorCode;
    private String errorDetails;

    public GoFeatureFlagResponse toGoFeatureFlagResponse() {
        val goff = new GoFeatureFlagResponse();
        goff.setValue(value);
        goff.setVariationType(variant);
        goff.setReason(reason);
        goff.setErrorCode(errorCode);
        goff.setErrorDetails(errorDetails);
        goff.setFailed(errorCode != null);

        val cacheable = metadata.get("gofeatureflag_cacheable");
        if (cacheable instanceof Boolean) {
            goff.setCacheable((Boolean) cacheable);
            metadata.remove("gofeatureflag_cacheable");
        }

        val version = metadata.get("gofeatureflag_version");
        if (version instanceof String) {
            goff.setVersion((String) version);
            metadata.remove("gofeatureflag_version");
        }
        goff.setMetadata(metadata);
        return goff;
    }

}
