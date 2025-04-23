package dev.openfeature.contrib.providers.v2.gofeatureflag.api.bean;

import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OfrepRequest {
    private Map<String, Object> context;
}
