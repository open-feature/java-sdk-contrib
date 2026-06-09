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
 * <p>
 * Flags with duplicate names across different flag arrays
 * (e.g., in {@link  OpenFeature#value()} and {@link  OpenFeature#booleanFlags()}
 * or {@link  OpenFeature#booleanFlags()} and {@link  OpenFeature#stringFlags()})
 * are not permitted and will result in an {@link IllegalArgumentException}.
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
    Flag[] value() default {};
    /**
     * Collection of {@link BooleanFlag} configurations for this domain.
     */
    BooleanFlag[] booleanFlags() default {};
    /**
     * Collection of {@link StringFlag} configurations for this domain.
     */
    StringFlag[] stringFlags() default {};
    /**
     * Collection of {@link IntegerFlag} configurations for this domain.
     */
    IntegerFlag[] integerFlags() default {};
    /**
     * Collection of {@link DoubleFlag} configurations for this domain.
     */
    DoubleFlag[] doubleFlags() default {};
}
