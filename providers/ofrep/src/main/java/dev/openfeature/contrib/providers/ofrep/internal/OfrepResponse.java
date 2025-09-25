package dev.openfeature.contrib.providers.ofrep.internal;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * This class represents the response from an OFREP response.
 */
@Getter
@Setter
@ToString
public class OfrepResponse {
    private Object value;
    private String key;
    private String variant;
    private String reason;
    private boolean cacheable;
    private String errorCode;
    private String errorDetails;
    private Map<String, Object> metadata;

    public Map<String, Object> getMetadata() {
        return ImmutableMap.copyOf(metadata);
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata != null ? ImmutableMap.copyOf(metadata) : ImmutableMap.of();
    }

    /**
     * Creates a copy of the current OfrepResponse instance.
     *
     * @return A new OfrepResponse instance with the same values as the provided instance.
     */
    public OfrepResponse copy() {
        OfrepResponse newResponse = new OfrepResponse();
        newResponse.value = this.value;
        newResponse.key = this.key;
        newResponse.variant = this.variant;
        newResponse.reason = this.reason;
        newResponse.cacheable = this.cacheable;
        newResponse.errorCode = this.errorCode;
        newResponse.errorDetails = this.errorDetails;
        newResponse.metadata = metadata != null ? ImmutableMap.copyOf(metadata) : ImmutableMap.of();
        return newResponse;
    }
}
