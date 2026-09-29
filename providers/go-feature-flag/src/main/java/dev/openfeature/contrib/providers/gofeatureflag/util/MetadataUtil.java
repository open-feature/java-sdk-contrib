package dev.openfeature.contrib.providers.gofeatureflag.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.openfeature.sdk.ImmutableMetadata;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * MetadataUtil is a utility class to convert the metadata received from the server to an
 * ImmutableMetadata format known by Open Feature.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MetadataUtil {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * convertFlagMetadata is converting the flagMetadata object received from the server to an
     * ImmutableMetadata format known by Open Feature.
     *
     * @param flagMetadata - metadata received from the server
     * @return a converted metadata object.
     */
    public static ImmutableMetadata convertFlagMetadata(Map<String, Object> flagMetadata) {
        ImmutableMetadata.ImmutableMetadataBuilder builder = ImmutableMetadata.builder();
        if (flagMetadata == null) {
            return builder.build();
        }
        flagMetadata.forEach((k, v) -> {
            if (v == null) {
                log.debug("skipping null metadata value for key {}", k);
            } else if (v instanceof String) {
                builder.addString(k, (String) v);
            } else if (v instanceof Long) {
                builder.addLong(k, (Long) v);
            } else if (v instanceof Integer) {
                builder.addInteger(k, (Integer) v);
            } else if (v instanceof Float) {
                builder.addFloat(k, (Float) v);
            } else if (v instanceof Double) {
                builder.addDouble(k, (Double) v);
            } else if (v instanceof Boolean) {
                builder.addBoolean(k, (Boolean) v);
            } else {
                builder.addString(k, asJson(k, v));
            }
        });
        return builder.build();
    }

    private static String asJson(final String key, final Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("metadata value for key {} could not be serialised to JSON", key, e);
            return String.valueOf(value);
        }
    }
}
