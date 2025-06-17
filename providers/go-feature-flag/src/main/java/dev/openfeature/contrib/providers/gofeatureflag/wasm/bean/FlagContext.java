package dev.openfeature.contrib.providers.gofeatureflag.wasm.bean;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * This class represents the context of a flag in the GO Feature Flag system.
 * It contains the default SDK value and the evaluation context enrichment.
 */
@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FlagContext {
    private Object defaultSdkValue;
    private Map<String, Object> evaluationContextEnrichment;
}
