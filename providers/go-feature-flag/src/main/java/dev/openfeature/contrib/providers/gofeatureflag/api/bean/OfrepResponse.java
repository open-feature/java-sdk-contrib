package dev.openfeature.contrib.providers.gofeatureflag.api.bean;

import dev.openfeature.contrib.providers.gofeatureflag.bean.GoFeatureFlagResponse;
import java.util.Map;
import lombok.Data;
import lombok.val;

/**
 * This class represents the response from an OFREP response.
 */
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

    /**
     * Converts the OFREP response to a GO Feature Flag response.
     *
     * @return the converted GO Feature Flag response
     */
    public GoFeatureFlagResponse toGoFeatureFlagResponse() {
        val goff = new GoFeatureFlagResponse();
        goff.setValue(value);
        goff.setVariationType(variant);
        goff.setReason(reason);
        goff.setErrorCode(errorCode);
        goff.setErrorDetails(errorDetails);
        goff.setFailed(errorCode != null);

        if (metadata != null) {
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
        }
        return goff;
    }

}
