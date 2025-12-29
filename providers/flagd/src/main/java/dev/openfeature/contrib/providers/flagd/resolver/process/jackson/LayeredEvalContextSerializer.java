package dev.openfeature.contrib.providers.flagd.resolver.process.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import dev.openfeature.sdk.LayeredEvaluationContext;
import java.io.IOException;

/**
 * Custom serializer for LayeredEvaluationContext.
 *
 * <p>This serializer iterates through the context's keys and writes each key-value pair
 * to the JSON output.
 */
public class LayeredEvalContextSerializer extends JsonSerializer<LayeredEvaluationContext> {

    @Override
    public void serialize(LayeredEvaluationContext ctx, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {
        gen.writeStartObject();

        // Use the keySet and getValue to stream the entries
        for (String key : ctx.keySet()) {
            Object value = ctx.getValue(key);
            gen.writeObjectField(key, value);
        }

        gen.writeEndObject();
    }
}
