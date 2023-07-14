package dev.openfeature.contrib.hooks.otel;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Represents an OTel dimension(attribute) Key and Type of the dimension.
 */
@Getter
@AllArgsConstructor
public class DimensionDescription {
    private final String key;
    private final Class type;
}
