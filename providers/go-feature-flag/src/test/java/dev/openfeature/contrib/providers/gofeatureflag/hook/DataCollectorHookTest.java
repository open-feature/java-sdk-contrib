package dev.openfeature.contrib.providers.gofeatureflag.hook;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.openfeature.contrib.providers.gofeatureflag.bean.IEvent;
import dev.openfeature.contrib.providers.gofeatureflag.evaluator.IEvaluator;
import dev.openfeature.contrib.providers.gofeatureflag.exception.InvalidOptions;
import dev.openfeature.contrib.providers.gofeatureflag.service.EventsPublisher;
import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.FlagValueType;
import dev.openfeature.sdk.HookContext;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.Reason;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.val;
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

    @SneakyThrows
    @Test
    void shouldRecordAnEvaluationThatWasNotServedFromACache() {
        EventsPublisher<IEvent> eventsPublisher = mock(EventsPublisher.class);
        val evaluator = mock(IEvaluator.class);
        when(evaluator.isFlagTrackable("flag")).thenReturn(true);
        val hook = new DataCollectorHook(DataCollectorHookOptions.builder()
                .eventsPublisher(eventsPublisher)
                .evaluator(evaluator)
                .build());

        hook.after(
                HookContext.<Boolean>from(
                        "flag", FlagValueType.BOOLEAN, null, null, new ImmutableContext("key"), false),
                FlagEvaluationDetails.<Boolean>builder()
                        .flagKey("flag")
                        .value(true)
                        .variant("enabled")
                        .reason(Reason.TARGETING_MATCH.name())
                        .build(),
                Map.of());

        verify(eventsPublisher).add(any());
    }
}
