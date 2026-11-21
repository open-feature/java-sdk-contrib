package dev.openfeature.contrib.hooks.otel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.FlagValueType;
import dev.openfeature.sdk.HookContext;
import dev.openfeature.sdk.ImmutableMetadata;
import dev.openfeature.sdk.MutableContext;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TracesHookTest {
    private static MockedStatic<Span> mockedSpan;

    @BeforeAll
    public static void init() {
        mockedSpan = mockStatic(Span.class);
    }

    @AfterAll
    public static void close() {
        mockedSpan.close();
    }

    private final AttributeKey<String> flagKeyAttributeKey = AttributeKey.stringKey("feature_flag.key");
    private final AttributeKey<String> providerNameAttributeKey = AttributeKey.stringKey("feature_flag.provider_name");
    private final AttributeKey<String> variantAttributeKey = AttributeKey.stringKey("feature_flag.variant");

    private final HookContext<String> hookContext = HookContext.<String>builder()
            .flagKey("test_key")
            .type(FlagValueType.STRING)
            .providerMetadata(() -> "test provider")
            .ctx(new MutableContext())
            .defaultValue("default")
            .build();

    @Mock
    private Span span;

    @Test
    @DisplayName("should add an event in span during after method execution")
    void should_add_event_in_span_during_after_method_execution() {
        FlagEvaluationDetails<String> details = FlagEvaluationDetails.<String>builder()
                .variant("test_variant")
                .value("variant_value")
                .build();
        mockedSpan.when(Span::current).thenReturn(span);

        TracesHook tracesHook = new TracesHook();
        tracesHook.after(hookContext, details, null);

        Attributes expectedAttr = Attributes.of(
                flagKeyAttributeKey,
                "test_key",
                providerNameAttributeKey,
                "test provider",
                variantAttributeKey,
                "test_variant");

        verify(span).addEvent("feature_flag", expectedAttr);
    }

    @Test
    @DisplayName("attribute should fallback to value field when variant is null")
    void attribute_should_fallback_to_value_field_when_variant_is_null() {
        FlagEvaluationDetails<String> details =
                FlagEvaluationDetails.<String>builder().value("variant_value").build();
        mockedSpan.when(Span::current).thenReturn(span);

        TracesHook tracesHook = new TracesHook();
        tracesHook.after(hookContext, details, null);

        Attributes expectedAttr = Attributes.of(
                flagKeyAttributeKey,
                "test_key",
                providerNameAttributeKey,
                "test provider",
                variantAttributeKey,
                "variant_value");

        verify(span).addEvent("feature_flag", expectedAttr);
    }

    @Test
    @DisplayName("should not call addEvent because there is no active span")
    void should_not_call_add_event_when_no_active_span() {
        HookContext<String> hookContext = HookContext.<String>builder()
                .flagKey("test_key")
                .type(FlagValueType.STRING)
                .providerMetadata(() -> "test provider")
                .ctx(new MutableContext())
                .defaultValue("default")
                .build();
        FlagEvaluationDetails<String> details = FlagEvaluationDetails.<String>builder()
                .variant(null)
                .value("variant_value")
                .build();
        mockedSpan.when(Span::current).thenReturn(null);

        TracesHook tracesHook = new TracesHook();
        tracesHook.after(hookContext, details, null);

        verifyNoInteractions(span);
    }

    @Test
    @DisplayName("should record an exception and avoid status changes in span during error method execution")
    void should_record_exception_and_status_in_span_during_error_method_execution() {
        RuntimeException runtimeException = new RuntimeException("could not resolve the flag");
        mockedSpan.when(Span::current).thenReturn(span);

        final TracesHook tracesHook = new TracesHook(TracesHookOptions.builder()
                .extraAttributes(Attributes.builder().put("scope", "app-a").build())
                .build());

        tracesHook.error(hookContext, runtimeException, null);

        final Attributes expectedAttr = Attributes.builder()
                .put(flagKeyAttributeKey, "test_key")
                .put(providerNameAttributeKey, "test provider")
                .put("scope", "app-a")
                .build();

        verify(span).recordException(runtimeException, expectedAttr);
        verify(span, times(0)).setStatus(any());
    }

    @Test
    @DisplayName("span error status must be set if overridden by option")
    void should_record_exception_but_not_status_in_span_with_options() {
        RuntimeException runtimeException = new RuntimeException("could not resolve the flag");
        mockedSpan.when(Span::current).thenReturn(span);

        TracesHook tracesHook = new TracesHook(
                TracesHookOptions.builder().setSpanErrorStatus(true).build());
        tracesHook.error(hookContext, runtimeException, null);

        Attributes expectedAttr =
                Attributes.of(flagKeyAttributeKey, "test_key", providerNameAttributeKey, "test provider");

        verify(span).recordException(runtimeException, expectedAttr);
        verify(span, times(1)).setStatus(any());
    }

    @Test
    @DisplayName("should not call recordException because there is no active span")
    void should_not_call_record_exception_when_no_active_span() {
        RuntimeException runtimeException = new RuntimeException("could not resolve the flag");
        mockedSpan.when(Span::current).thenReturn(null);

        TracesHook tracesHook = new TracesHook();
        tracesHook.error(hookContext, runtimeException, null);

        verifyNoInteractions(span);
    }

    @Test
    @DisplayName("should execute callback which populate span attributes")
    void should_execute_callback_which_populate_span_attributes() {
        FlagEvaluationDetails<String> details = FlagEvaluationDetails.<String>builder()
                .variant("test_variant")
                .value("variant_value")
                .flagMetadata(ImmutableMetadata.builder()
                        .addBoolean("boolean", true)
                        .addInteger("integer", 1)
                        .addLong("long", 1L)
                        .addFloat("float", 1.0F)
                        .addDouble("double", 1.0D)
                        .addString("string", "string")
                        .build())
                .build();
        mockedSpan.when(Span::current).thenReturn(span);

        TracesHookOptions options = TracesHookOptions.builder()
                .dimensionExtractor(metadata -> Attributes.builder()
                        .put("boolean", metadata.getBoolean("boolean"))
                        .put("integer", metadata.getInteger("integer"))
                        .put("long", metadata.getLong("long"))
                        .put("float", metadata.getFloat("float"))
                        .put("double", metadata.getDouble("double"))
                        .put("string", metadata.getString("string"))
                        .build())
                .extraAttributes(Attributes.builder().put("scope", "value").build())
                .build();

        TracesHook tracesHook = new TracesHook(options);
        tracesHook.after(hookContext, details, null);

        final AttributesBuilder attributesBuilder = Attributes.builder();
        attributesBuilder.put(flagKeyAttributeKey, "test_key");
        attributesBuilder.put(providerNameAttributeKey, "test provider");
        attributesBuilder.put(variantAttributeKey, "test_variant");
        attributesBuilder.put("boolean", true);
        attributesBuilder.put("integer", 1);
        attributesBuilder.put("long", 1L);
        attributesBuilder.put("float", 1.0F);
        attributesBuilder.put("double", 1.0D);
        attributesBuilder.put("string", "string");
        attributesBuilder.put("scope", "value");

        // verify span built with given attributes
        verify(span).addEvent("feature_flag", attributesBuilder.build());
    }

    @ParameterizedTest
    @MethodSource("nullAttributeOptions")
    @DisplayName("otel should not log warnings when attributes are null")
    void otel_should_not_log_warnings_when_attributes_are_null(TracesHook hook) {
        final List<String> badLogs = new ArrayList<>();
        final Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                    badLogs.add(record.getMessage());
                }
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        };
        final Logger rootLogger = Logger.getLogger("");
        rootLogger.addHandler(handler);

        try {
            mockedSpan.when(Span::current).thenReturn(span);

            hook.error(hookContext, new RuntimeException(), null);
            hook.after(
                    hookContext,
                    FlagEvaluationDetails.<String>builder()
                            .value("variant_value")
                            .build(),
                    null);
        } finally {
            rootLogger.removeHandler(handler);
        }

        assertThat(badLogs).isEmpty();
    }

    private static Stream<Arguments> nullAttributeOptions() {
        return Stream.of(
                Arguments.of(new TracesHook()),
                Arguments.of(new TracesHook(TracesHookOptions.builder().build())),
                Arguments.of(new TracesHook(
                        TracesHookOptions.builder().extraAttributes(null).build())),
                Arguments.of(new TracesHook(TracesHookOptions.builder()
                        .dimensionExtractor(m -> null)
                        .build())));
    }
}
