package dev.openfeature.contrib.providers.gofeatureflag.util;

import dev.openfeature.sdk.ImmutableMetadata;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * MetadataUtil is a utility class to convert the metadata received from the server to an
 * ImmutableMetadata format known by Open Feature.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MetadataUtil {
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
            if (v instanceof Long) {
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
                builder.addString(k, v.toString());
            }
        });
        return builder.build();
    }
}
