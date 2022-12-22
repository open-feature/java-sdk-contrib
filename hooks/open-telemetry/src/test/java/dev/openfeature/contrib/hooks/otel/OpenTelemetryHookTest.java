package dev.openfeature.contrib.hooks.otel;

import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.FlagValueType;
import dev.openfeature.sdk.HookContext;
import dev.openfeature.sdk.MutableContext;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.internal.matchers.Any;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class OpenTelemetryHookTest {

    private OpenTelemetryHook openTelemetryHook = new OpenTelemetryHook();

    private final AttributeKey<String> flagKeyAttributeKey = AttributeKey.stringKey("feature_flag.flag_key");

    private final AttributeKey<String> providerNameAttributeKey = AttributeKey.stringKey("feature_flag.provider_name");

    private final AttributeKey<String> variantAttributeKey = AttributeKey.stringKey("feature_flag.variant");

    private static MockedStatic<Span> mockedSpan;

    @Mock private Span span;

    private HookContext<String> hookContext = HookContext.<String>builder()
            .flagKey("test_key")
            .type(FlagValueType.STRING)
            .providerMetadata(() -> "test provider")
            .ctx(new MutableContext())
            .defaultValue("default")
            .build();

    @BeforeAll
    public static void init() {
        mockedSpan = mockStatic(Span.class);
    }

    @AfterAll
    public static void close() {
        mockedSpan.close();
    }

    @Test
    @DisplayName("should add an event in span during after method execution")
    void should_add_event_in_span_during_after_method_execution() {
        FlagEvaluationDetails<String> details = FlagEvaluationDetails.<String>builder()
                .variant("test_variant")
                .value("variant_value")
                .build();
        mockedSpan.when(Span::current).thenReturn(span);
        openTelemetryHook.after(hookContext, details, null);
        Attributes expectedAttr = Attributes.of(flagKeyAttributeKey, "test_key",
                providerNameAttributeKey, "test provider",
                variantAttributeKey, "test_variant");
        verify(span).addEvent("feature_flag", expectedAttr);
    }

    @Test
    @DisplayName("attribute should fallback to value field when variant is null")
    void attribute_should_fallback_to_value_field_when_variant_is_null() {
        FlagEvaluationDetails<String> details = FlagEvaluationDetails.<String>builder()
                .value("variant_value")
                .build();
        mockedSpan.when(Span::current).thenReturn(span);
        openTelemetryHook.after(hookContext, details, null);
        Attributes expectedAttr = Attributes.of(flagKeyAttributeKey, "test_key",
                providerNameAttributeKey, "test provider",
                variantAttributeKey, "variant_value");
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
        openTelemetryHook.after(hookContext, details, null);
        verifyNoInteractions(span);
    }

    @Test
    @DisplayName("should record an exception in span during error method execution")
    void should_record_exception_in_span_during_error_method_execution() {
        RuntimeException runtimeException = new RuntimeException("could not resolve the flag");
        mockedSpan.when(Span::current).thenReturn(span);
        openTelemetryHook.error(hookContext, runtimeException, null);
        Attributes expectedAttr = Attributes.of(flagKeyAttributeKey, "test_key",
                providerNameAttributeKey, "test provider");
        verify(span).recordException(runtimeException, expectedAttr);
    }

    @Test
    @DisplayName("should not call recordException because there is no active span")
    void should_not_call_record_exception_when_no_active_span() {
        RuntimeException runtimeException = new RuntimeException("could not resolve the flag");
        mockedSpan.when(Span::current).thenReturn(null);
        openTelemetryHook.error(hookContext, runtimeException, null);
        verifyNoInteractions(span);
    }

}