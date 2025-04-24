package dev.openfeature.contrib.providers.gofeatureflag.hook;

import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.Hook;
import dev.openfeature.sdk.HookContext;
import dev.openfeature.sdk.MutableContext;
import dev.openfeature.sdk.MutableStructure;
import dev.openfeature.sdk.Value;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * EnrichEvaluationContextHook is an OpenFeature Hook in charge of enriching the evaluation context.
 */
public class EnrichEvaluationContextHook implements Hook<String> {
    private final Map<String, Object> exporterMetadata;

    public EnrichEvaluationContextHook(Map<String, Object> exporterMetadata) {
        this.exporterMetadata = Optional.ofNullable(exporterMetadata).orElseGet(HashMap::new);
    }

    @Override
    public Optional<EvaluationContext> before(HookContext<String> ctx, Map<String, Object> hints) {
        if (ctx == null) {
            return Optional.empty();
        }

        MutableContext mutableContext =
                new MutableContext(ctx.getCtx().getTargetingKey(), ctx.getCtx().asMap());

        MutableStructure metadata = new MutableStructure();
        for (Map.Entry<String, Object> entry : exporterMetadata.entrySet()) {
            switch (entry.getValue().getClass().getSimpleName()) {
                case "String":
                    metadata.add(entry.getKey(), (String) entry.getValue());
                    break;
                case "Boolean":
                    metadata.add(entry.getKey(), (Boolean) entry.getValue());
                    break;
                case "Integer":
                    metadata.add(entry.getKey(), (Integer) entry.getValue());
                    break;
                case "Double":
                    metadata.add(entry.getKey(), (Double) entry.getValue());
                    break;
                default:
                    throw new IllegalArgumentException(
                            "Unsupported type: " + entry.getValue().getClass().getSimpleName());
            }
        }
        Map<String, Value> expMetadata = new HashMap<>();
        expMetadata.put("exporterMetadata", new Value(metadata));
        mutableContext.add("gofeatureflag", new MutableStructure(expMetadata));
        return Optional.of(mutableContext);
    }
}
