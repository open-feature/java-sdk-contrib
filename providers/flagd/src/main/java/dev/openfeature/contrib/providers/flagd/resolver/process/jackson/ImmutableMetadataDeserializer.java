package dev.openfeature.contrib.providers.flagd.resolver.process.jackson;

import com.fasterxml.jackson.databind.JsonDeserializer;
import dev.openfeature.sdk.ImmutableMetadata;
import java.io.IOException;
import java.util.Map;

/**
 * Custom deserializer for ImmutableMetadata that preserves value types.
 *
 * <p>This deserializer properly handles different value types (Boolean, String, Integer, Long,
 * Double, Float) instead of converting everything to strings.
 */
public class ImmutableMetadataDeserializer extends JsonDeserializer<ImmutableMetadata> {

    @Override
    public ImmutableMetadata deserialize(
            com.fasterxml.jackson.core.JsonParser p, com.fasterxml.jackson.databind.DeserializationContext ctxt)
            throws IOException {
        @SuppressWarnings("unchecked")
        Map<String, Object> map = p.readValueAs(Map.class);
        ImmutableMetadata.ImmutableMetadataBuilder builder = ImmutableMetadata.builder();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            addEntryToMetadataBuilder(builder, entry.getKey(), entry.getValue());
        }
        return builder.build();
    }

    private static void addEntryToMetadataBuilder(
            ImmutableMetadata.ImmutableMetadataBuilder metadataBuilder, String key, Object value) {
        if (value instanceof Number) {
            if (value instanceof Long) {
                metadataBuilder.addLong(key, (Long) value);
            } else if (value instanceof Double) {
                metadataBuilder.addDouble(key, (Double) value);
            } else if (value instanceof Integer) {
                metadataBuilder.addInteger(key, (Integer) value);
            } else if (value instanceof Float) {
                metadataBuilder.addFloat(key, (Float) value);
            } else {
                // Fallback for other Number types
                metadataBuilder.addDouble(key, ((Number) value).doubleValue());
            }
        } else if (value instanceof Boolean) {
            metadataBuilder.addBoolean(key, (Boolean) value);
        } else if (value instanceof String) {
            metadataBuilder.addString(key, (String) value);
        } else if (value != null) {
            // Fallback to string for unknown types
            metadataBuilder.addString(key, value.toString());
        }
    }
}
