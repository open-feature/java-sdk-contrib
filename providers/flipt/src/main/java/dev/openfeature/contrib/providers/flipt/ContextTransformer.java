package dev.openfeature.contrib.providers.flipt;

import dev.openfeature.sdk.EvaluationContext;

import java.util.HashMap;
import java.util.Map;

/**
 * Transformer from OpenFeature context to Flipt context.
 */
public class ContextTransformer {

    protected static Map<String, String> transform(EvaluationContext ctx) {
        Map<String, String> contextMap = new HashMap<>();
        ctx.asObjectMap().forEach((k, v) -> {
            contextMap.put(k, String.valueOf(v));
        });
        return contextMap;
    }
}
