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
import lombok.val;

/**
 * EnrichEvaluationContextHook is an OpenFeature Hook in charge of enriching the evaluation context.
 */
public class EnrichEvaluationContextHook implements Hook<String> {
    private static final String GOFEATUREFLAG_KEY = "gofeatureflag";
    private static final String EXPORTER_METADATA_KEY = "exporterMetadata";
    private final Map<String, Object> exporterMetadata;

    public EnrichEvaluationContextHook(Map<String, Object> exporterMetadata) {
        this.exporterMetadata = Optional.ofNullable(exporterMetadata).orElseGet(HashMap::new);
    }

    @Override
    public Optional<EvaluationContext> before(HookContext<String> ctx, Map<String, Object> hints) {
        if (ctx == null) {
            return Optional.empty();
        }
        if (this.exporterMetadata == null || this.exporterMetadata.isEmpty()) {
            return Optional.of(ctx.getCtx());
        }

        final MutableContext mutableContext =
                new MutableContext(ctx.getCtx().getTargetingKey(), ctx.getCtx().asMap());

        final MutableStructure metadata = new MutableStructure();
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

        Map<String, Value> goffNamespace = new HashMap<>();
        val existing = ctx.getCtx().getValue(GOFEATUREFLAG_KEY);
        if (existing != null && existing.isStructure()) {
            goffNamespace.putAll(existing.asStructure().asMap());
        }
        goffNamespace.put(EXPORTER_METADATA_KEY, new Value(metadata));
        mutableContext.add(GOFEATUREFLAG_KEY, new MutableStructure(goffNamespace));
        return Optional.of(mutableContext);
    }
}
