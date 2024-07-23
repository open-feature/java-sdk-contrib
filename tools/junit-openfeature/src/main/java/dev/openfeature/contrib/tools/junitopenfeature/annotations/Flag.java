package dev.openfeature.contrib.tools.junitopenfeature.annotations;

import dev.openfeature.contrib.tools.junitopenfeature.OpenFeatureExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for Flag Configuration for the default domain.
 * Can be used as a standalone flag configuration but also within {@link OpenFeature}.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(Flags.class)
@ExtendWith(OpenFeatureExtension.class)
public @interface Flag {
    /**
     * The key of the FeatureFlag.
     */
    String name();

    /**
     * The value of the FeatureFlag.
     */
    String value();

    /**
     * The type of the FeatureFlag.
     */
    Class<?> valueType() default Boolean.class;
}


