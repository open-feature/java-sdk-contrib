package dev.openfeature.contrib.providers.flagd.resolver.common;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapSetter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * FlagdGrpcInterceptor is an interceptor for grpc communication from java-sdk to flagd.
 * <a href="https://github.com/open-telemetry/opentelemetry-java-docs">credits</a>
 */
public final class FlagdGrpcInterceptor implements ClientInterceptor {
    private static final TextMapSetter<Metadata> SETTER = new Setter();

    private final OpenTelemetry openTelemetry;

    /**
     * Intercept gRPC communication for propagating OpenTelemetry context over the wire.
     *
     * @param openTelemetry the OpenTelemetry implementation.
     */
    public FlagdGrpcInterceptor(@Nonnull final OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
    }

    @Override public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> methodDescriptor,
                                                                         CallOptions callOptions, Channel channel) {

        final ClientCall<ReqT, RespT> call = channel.newCall(methodDescriptor, callOptions);

        return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(call) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                openTelemetry.getPropagators().getTextMapPropagator().inject(Context.current(), headers, SETTER);
                super.start(responseListener, headers);
            }
        };
    }

    /**
     * Setter implements TextMapSetter with carrier check.
     */
    static class Setter implements TextMapSetter<Metadata> {
        @Override
        public void set(@Nullable Metadata carrier, String key, String value) {
            if (carrier == null) {
                return;
            }

            carrier.put(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER), value);
        }
    }
}
