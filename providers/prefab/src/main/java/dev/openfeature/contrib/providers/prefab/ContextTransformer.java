package dev.openfeature.contrib.providers.prefab;

import cloud.prefab.context.PrefabContext;
import dev.openfeature.sdk.EvaluationContext;

/**
 * Transformer from OpenFeature context to Prefab context.
 */
public class ContextTransformer {

    protected static PrefabContext transform(EvaluationContext ctx) {
        PrefabContext.Builder contextBuilder = PrefabContext.newBuilder("User");
        ctx.asObjectMap().forEach((k, v) -> contextBuilder.put(k, String.valueOf(v)));
        return contextBuilder.build();
    }

}
