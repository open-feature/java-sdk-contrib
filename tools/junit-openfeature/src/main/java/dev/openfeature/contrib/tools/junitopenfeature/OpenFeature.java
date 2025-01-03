package dev.openfeature.contrib.tools.junitopenfeature;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Annotation for generating an extended configuration for OpenFeature.
 * This annotation allows you to specify a list of flags for a specific domain.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(value = OpenFeatures.class)
@ExtendWith(OpenFeatureExtension.class)
public @interface OpenFeature {
    /**
     * the provider domain used for this configuration.
     */
    String domain() default "";
    /**
     * Collection of {@link Flag} configurations for this domain.
     */
    Flag[] value();
}
