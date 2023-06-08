package dev.openfeature.contrib.hooks.otel;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Represents a Key and Type of dimension.
 */
@Getter
@AllArgsConstructor
public class DimensionDescription {
    private final String key;
    private final Class type;
}
