package dev.openfeature.contrib.providers.flagd;


import com.google.protobuf.Message;
import dev.openfeature.contrib.providers.flagd.strategy.TracedResolving;
import dev.openfeature.flagd.grpc.Schema;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TracedResolvingTest {

    @Test
    public void basicTest(){
        // given
        final String key = "flagA";
        final OpenTelemetry openTelemetry = mock(OpenTelemetry.class);
        final Tracer tracer = mock(Tracer.class);
        final SpanBuilder spanBuilder = mock(SpanBuilder.class);
        final Span span = mock(Span.class);
        final Message message = mock(Message.class);

        // when
        when(openTelemetry.getTracer(anyString())).thenReturn(tracer);
        when(tracer.spanBuilder(anyString())).thenReturn(spanBuilder);
        when(spanBuilder.setSpanKind(any())).thenReturn(spanBuilder);
        when(spanBuilder.startSpan()).thenReturn(span);

        final TracedResolving tracedResolving = new TracedResolving(openTelemetry);
        tracedResolving.resolve(m -> message, Schema.ResolveBooleanRequest.newBuilder().build(), key);

        // then
        verify(tracer, times(1)).spanBuilder(anyString());

        verify(spanBuilder, times(1)).setSpanKind(any());
        verify(spanBuilder, times(1)).startSpan();

        verify(span, times(1)).setAttribute("feature_flag.key", key);
        verify(span, times(1)).setAttribute("feature_flag.provider_name", "flagd");
        verify(span, times(1)).end();
    }

}