package dev.openfeature.contrib.providers.gofeatureflag.hook;

import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.openfeature.contrib.providers.gofeatureflag.exception.InvalidOptions;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

public class DataCollectorHookTest {
    @SneakyThrows
    @Test
    void shouldErrorIfNoOptionsProvided() {
        assertThrows(InvalidOptions.class, () -> new DataCollectorHook(null));
    }

    @SneakyThrows
    @Test
    void shouldErrorIfNoEventsPublisherProvided() {
        assertThrows(
                InvalidOptions.class,
                () -> new DataCollectorHook(DataCollectorHookOptions.builder().build()));
    }
}
