package dev.openfeature.contrib.tools.junitopenfeature;

import dev.openfeature.sdk.FlagValueType;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.*;

@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(value = OpenFeatures.class)
@ExtendWith(OpenFeatureExtension.class)
public @interface OpenFeature {
    String domain() default "";
    Flag[] value();
}


