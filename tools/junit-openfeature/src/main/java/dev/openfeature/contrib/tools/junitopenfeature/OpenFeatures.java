package dev.openfeature.contrib.tools.junitopenfeature;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.*;

@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(OpenFeatureExtension.class)
public @interface OpenFeatures {
    OpenFeature[] value() default {};
}


