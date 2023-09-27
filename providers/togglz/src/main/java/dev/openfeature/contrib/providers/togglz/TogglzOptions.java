package dev.openfeature.contrib.providers.togglz;

import lombok.Builder;
import lombok.Data;
import org.togglz.core.Feature;

import java.util.Collection;

/**
 * Togglz Options for initializing Togglz provider.
 */
@Data
@Builder
public class TogglzOptions {
    Collection<Feature> features;
}
