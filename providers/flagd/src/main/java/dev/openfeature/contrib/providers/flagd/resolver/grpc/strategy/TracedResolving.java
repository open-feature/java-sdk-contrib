package dev.openfeature.contrib.providers.flagd.resolver.grpc.strategy;

import com.google.protobuf.Message;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

import javax.annotation.Nonnull;
import java.util.function.Function;

/**
 * {@link TracedResolving} a request to response resolver with tracing for telemetry.
 */
public class TracedResolving implements ResolveStrategy {

    private final Tracer tracer;

    public TracedResolving(@Nonnull OpenTelemetry telemetry) {
        this.tracer = telemetry.getTracer("OpenFeature/dev.openfeature.contrib.providers.flagd");
    }

    @Override
    public <ReqT extends Message, ResT extends Message> ResT resolve(final Function<ReqT, ResT> resolverRef,
                                                                     final Message req, final String key) {

        final Span span = tracer.spanBuilder("resolve").setSpanKind(SpanKind.CLIENT).startSpan();
        span.setAttribute("feature_flag.key", key);
        span.setAttribute("feature_flag.provider_name", "flagd");

        try (Scope scope = span.makeCurrent()) {
            return resolverRef.apply((ReqT) req);
        } finally {
            span.end();
        }
    }
}
