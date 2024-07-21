package dev.openfeature.contrib.tools.junitopenfeature;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(Flags.class)
@ExtendWith(OpenFeatureExtension.class)
public @interface Flag {
    String name();

    String value();

    Class<?> valueType() default Boolean.class;
}


