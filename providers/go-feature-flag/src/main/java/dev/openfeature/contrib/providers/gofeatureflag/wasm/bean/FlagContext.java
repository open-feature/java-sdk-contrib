package dev.openfeature.contrib.providers.gofeatureflag.wasm.bean;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FlagContext {
    private Object defaultSdkValue;
    private Map<String, Object> evaluationContextEnrichment;
}
