package dev.openfeature.contrib.providers.prefab;

import cloud.prefab.context.PrefabContext;
import cloud.prefab.context.PrefabContextSet;
import cloud.prefab.context.PrefabContextSetReadable;
import dev.openfeature.sdk.EvaluationContext;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Transformer from OpenFeature context to Prefab context.
 */
public class ContextTransformer {

    private ContextTransformer() {}

    protected static PrefabContextSetReadable transform(EvaluationContext ctx) {
        Map<String, PrefabContext.Builder> contextsMap = new HashMap<>();
        ctx.asObjectMap().forEach((k, v) -> {
            String[] parts = k.split("\\.", 2);
            if (parts.length < 2) {
                throw new IllegalArgumentException("context key structure should be in the form of x.y: " + k);
            }
            contextsMap.putIfAbsent(parts[0], PrefabContext.newBuilder(parts[0]));
            PrefabContext.Builder contextBuilder = contextsMap.get(parts[0]);
            contextBuilder.put(parts[1], Objects.toString(v, null));
        });
        PrefabContextSet prefabContextSet = new PrefabContextSet();
        contextsMap.forEach((key, value) -> {
            PrefabContext prefabContext = value.build();
            prefabContextSet.addContext(prefabContext);
        });

        return prefabContextSet;
    }
}
