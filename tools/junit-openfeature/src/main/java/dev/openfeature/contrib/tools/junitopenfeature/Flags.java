package dev.openfeature.contrib.tools.junitopenfeature;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Collection of {@link Flag} configurations.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(OpenFeatureExtension.class)
public @interface Flags {
    /**
     * Collection of {@link Flag} configurations.
     */
    Flag[] value() default {};
}
