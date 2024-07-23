package dev.openfeature.contrib.tools.junitopenfeature.annotations;

import dev.openfeature.contrib.tools.junitopenfeature.OpenFeatureExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Collection of {@link OpenFeature} configurations.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(OpenFeatureExtension.class)
public @interface OpenFeatures {
    /**
     * Collection of {@link OpenFeature} configurations.
     */
    OpenFeature[] value() default {};
}


