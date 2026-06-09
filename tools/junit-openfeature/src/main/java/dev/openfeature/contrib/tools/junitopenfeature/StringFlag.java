package dev.openfeature.contrib.tools.junitopenfeature;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Repeatable annotation that allows you to define String feature flags for the default domain.
 * Can be used as a standalone flag configuration but also within {@link OpenFeature}.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(StringFlags.class)
@ExtendWith(OpenFeatureExtension.class)
public @interface StringFlag {
    /**
     * The key of the FeatureFlag.
     */
    String name();

    /**
     * The value of the FeatureFlag.
     */
    String value();
}
