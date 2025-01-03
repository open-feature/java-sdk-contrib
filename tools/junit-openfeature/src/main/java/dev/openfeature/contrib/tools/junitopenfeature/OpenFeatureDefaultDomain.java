package dev.openfeature.contrib.tools.junitopenfeature;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Configuration of a default domain for standalone {@link Flag} configurations.
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(OpenFeatureExtension.class)
public @interface OpenFeatureDefaultDomain {
    /**
     * the default domain.
     */
    String value() default "";
}
