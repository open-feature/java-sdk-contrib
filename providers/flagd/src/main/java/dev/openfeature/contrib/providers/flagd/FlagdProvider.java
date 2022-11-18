package dev.openfeature.contrib.providers.flagd;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.net.ssl.SSLException;

import dev.openfeature.flagd.grpc.Schema.ResolveBooleanRequest;
import dev.openfeature.flagd.grpc.Schema.ResolveBooleanResponse;
import dev.openfeature.flagd.grpc.Schema.ResolveFloatRequest;
import dev.openfeature.flagd.grpc.Schema.ResolveFloatResponse;
import dev.openfeature.flagd.grpc.Schema.ResolveIntRequest;
import dev.openfeature.flagd.grpc.Schema.ResolveIntResponse;
import dev.openfeature.flagd.grpc.Schema.ResolveObjectRequest;
import dev.openfeature.flagd.grpc.Schema.ResolveObjectResponse;
import dev.openfeature.flagd.grpc.Schema.ResolveStringRequest;
import dev.openfeature.flagd.grpc.Schema.ResolveStringResponse;
import dev.openfeature.flagd.grpc.ServiceGrpc;
import dev.openfeature.flagd.grpc.ServiceGrpc.ServiceBlockingStub;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.Metadata;
import dev.openfeature.sdk.MutableStructure;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Value;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.channel.epoll.EpollDomainSocketChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.handler.ssl.SslContextBuilder;
import lombok.extern.slf4j.Slf4j;

/**
 * OpenFeature provider for flagd.
 */
@Slf4j
public class FlagdProvider implements FeatureProvider {

    static final String PROVIDER_NAME = "flagD Provider";
    static final String DEFAULT_PORT = "8013";
    static final String DEFAULT_TLS = "false";
    static final String DEFAULT_HOST = "localhost";
    static final int DEFAULT_DEADLINE = 500;

    static final String HOST_ENV_VAR_NAME = "FLAGD_HOST";
    static final String PORT_ENV_VAR_NAME = "FLAGD_PORT";
    static final String TLS_ENV_VAR_NAME = "FLAGD_TLS";
    static final String SOCKET_PATH_ENV_VAR_NAME = "FLAGD_SOCKET_PATH";
    static final String SERVER_CERT_PATH_ENV_VAR_NAME = "FLAGD_SERVER_CERT_PATH";

    private long deadline = DEFAULT_DEADLINE;
    private ServiceBlockingStub serviceStub;

    /**
     * Create a new FlagdProvider instance.
     *
     * @param socketPath unix socket path
     */
    public FlagdProvider(String socketPath) {
        this(buildServiceStub(null, null, null, null, socketPath));
    }

    /**
     * Create a new FlagdProvider instance.
     *
     * @param host     flagd server host, defaults to "localhost"
     * @param port     flagd server port, defaults to 8013
     * @param tls      use TLS, defaults to false
     * @param certPath path for server certificate, defaults to null to, using
     *                 system certs
     *
     */
    public FlagdProvider(String host, int port, boolean tls, String certPath) {
        this(buildServiceStub(host, port, tls, certPath, null));
    }

    /**
     * Create a new FlagdProvider instance.
     */
    public FlagdProvider() {
        this(buildServiceStub(null, null, null, null, null));
    }

    FlagdProvider(ServiceBlockingStub serviceStub) {
        this.serviceStub = serviceStub;
    }

    @Override
    public Metadata getMetadata() {
        return new Metadata() {
            @Override
            public String getName() {
                return PROVIDER_NAME;
            }
        };
    }

    @Override
    public ProviderEvaluation<Boolean> getBooleanEvaluation(String key, Boolean defaultValue,
            EvaluationContext ctx) {

        ResolveBooleanRequest request = ResolveBooleanRequest.newBuilder()
                .setFlagKey(key)
                .setContext(this.convertContext(ctx))
                .build();
        ResolveBooleanResponse r = this.serviceStub.withDeadlineAfter(this.deadline, TimeUnit.MILLISECONDS)
                .resolveBoolean(request);
        return ProviderEvaluation.<Boolean>builder()
                .value(r.getValue())
                .variant(r.getVariant())
                .reason(r.getReason())
                .build();
    }

    @Override
    public ProviderEvaluation<String> getStringEvaluation(String key, String defaultValue,
            EvaluationContext ctx) {
        ResolveStringRequest request = ResolveStringRequest.newBuilder()
                .setFlagKey(key)
                .setContext(this.convertContext(ctx)).build();
        ResolveStringResponse r = this.serviceStub.withDeadlineAfter(this.deadline, TimeUnit.MILLISECONDS)
                .resolveString(request);
        return ProviderEvaluation.<String>builder().value(r.getValue())
                .variant(r.getVariant())
                .reason(r.getReason())
                .build();
    }

    @Override
    public ProviderEvaluation<Double> getDoubleEvaluation(String key, Double defaultValue,
            EvaluationContext ctx) {
        ResolveFloatRequest request = ResolveFloatRequest.newBuilder()
                .setFlagKey(key)
                .setContext(this.convertContext(ctx))
                .build();
        ResolveFloatResponse r = this.serviceStub.withDeadlineAfter(this.deadline, TimeUnit.MILLISECONDS)
                .resolveFloat(request);
        return ProviderEvaluation.<Double>builder()
                .value(r.getValue())
                .variant(r.getVariant())
                .reason(r.getReason())
                .build();
    }

    @Override
    public ProviderEvaluation<Integer> getIntegerEvaluation(String key, Integer defaultValue,
            EvaluationContext ctx) {
        ResolveIntRequest request = ResolveIntRequest.newBuilder()
                .setFlagKey(key)
                .setContext(this.convertContext(ctx))
                .build();
        ResolveIntResponse r = this.serviceStub.withDeadlineAfter(this.deadline, TimeUnit.MILLISECONDS)
                .resolveInt(request);
        return ProviderEvaluation.<Integer>builder()
                .value((int) r.getValue())
                .variant(r.getVariant())
                .reason(r.getReason())
                .build();
    }

    @Override
    public ProviderEvaluation<Value> getObjectEvaluation(String key, Value defaultValue,
            EvaluationContext ctx) {
        ResolveObjectRequest request = ResolveObjectRequest.newBuilder()
                .setFlagKey(key)
                .setContext(this.convertContext(ctx))
                .build();
        ResolveObjectResponse r = this.serviceStub.withDeadlineAfter(this.deadline, TimeUnit.MILLISECONDS)
                .resolveObject(request);
        return ProviderEvaluation.<Value>builder()
                .value(this.convertObjectResponse(r.getValue()))
                .variant(r.getVariant())
                .reason(r.getReason())
                .build();
    }

    /**
     * Sets how long to wait for an evaluation. 
     *
     * @param deadlineMs time to wait before gRPC call is cancelled. Defaults to 10ms.
     * @return FlagdProvider
     */
    FlagdProvider setDeadline(long deadlineMs) {
        this.deadline = deadlineMs;
        return this;
    }

    /**
     * Recursively convert protobuf structure to openfeature value.
     */
    private Value convertObjectResponse(com.google.protobuf.Struct protobuf) {
        return this.convertProtobufMap(protobuf.getFieldsMap());
    }

    /**
     * Recursively convert the Evaluation context to a protobuf structure.
     */
    private com.google.protobuf.Struct convertContext(EvaluationContext ctx) {
        return this.convertMap(ctx.asMap()).getStructValue();
    }

    /**
     * Convert any openfeature value to a protobuf value.
     */
    private com.google.protobuf.Value convertAny(Value value) {
        if (value.isList()) {
            return this.convertList(value.asList());
        } else if (value.isStructure()) {
            return this.convertMap(value.asStructure().asMap());
        } else {
            return this.convertPrimitive(value);
        }
    }

    /**
     * Convert any protobuf value to an openfeature value.
     */
    private Value convertAny(com.google.protobuf.Value protobuf) {
        if (protobuf.hasListValue()) {
            return this.convertList(protobuf.getListValue());
        } else if (protobuf.hasStructValue()) {
            return this.convertProtobufMap(protobuf.getStructValue().getFieldsMap());
        } else {
            return this.convertPrimitive(protobuf);
        }
    }

    /**
     * Convert openfeature map to protobuf map.
     */
    private com.google.protobuf.Value convertMap(Map<String, Value> map) {
        Map<String, com.google.protobuf.Value> values = new HashMap<>();

        map.keySet().stream().forEach((String key) -> {
            Value value = map.get(key);
            values.put(key, this.convertAny(value));
        });
        com.google.protobuf.Struct struct = com.google.protobuf.Struct.newBuilder()
                .putAllFields(values).build();
        return com.google.protobuf.Value.newBuilder().setStructValue(struct).build();
    }

    /**
     * Convert protobuf map to openfeature map.
     */
    private Value convertProtobufMap(Map<String, com.google.protobuf.Value> map) {
        Map<String, Value> values = new HashMap<>();

        map.keySet().stream().forEach((String key) -> {
            com.google.protobuf.Value value = map.get(key);
            values.put(key, this.convertAny(value));
        });
        return new Value(new MutableStructure(values));
    }

    /**
     * Convert openfeature list to protobuf list.
     */
    private com.google.protobuf.Value convertList(List<Value> values) {
        com.google.protobuf.ListValue list = com.google.protobuf.ListValue.newBuilder()
                .addAllValues(values.stream()
                        .map(v -> this.convertAny(v)).collect(Collectors.toList()))
                .build();
        return com.google.protobuf.Value.newBuilder().setListValue(list).build();
    }

    /**
     * Convert protobuf list to openfeature list.
     */
    private Value convertList(com.google.protobuf.ListValue protobuf) {
        return new Value(protobuf.getValuesList().stream().map(p -> this.convertAny(p)).collect(Collectors.toList()));
    }

    /**
     * Convert openfeature value to protobuf value.
     */
    private com.google.protobuf.Value convertPrimitive(Value value) {
        com.google.protobuf.Value.Builder builder = com.google.protobuf.Value.newBuilder();

        if (value.isBoolean()) {
            builder.setBoolValue(value.asBoolean());
        } else if (value.isString()) {
            builder.setStringValue(value.asString());
        } else if (value.isNumber()) {
            builder.setNumberValue(value.asDouble());
        } else {
            builder.setNullValue(null);
        }
        return builder.build();
    }

    /**
     * Convert protobuf value openfeature value.
     */
    private Value convertPrimitive(com.google.protobuf.Value protobuf) {
        Value value;
        if (protobuf.hasBoolValue()) {
            value = new Value(protobuf.getBoolValue());
        } else if (protobuf.hasStringValue()) {
            value = new Value(protobuf.getStringValue());
        } else if (protobuf.hasNumberValue()) {
            value = new Value(protobuf.getNumberValue());
        } else {
            value = new Value();
        }
        return value;
    }

    private static ServiceBlockingStub buildServiceStub(String host, Integer port, Boolean tls, String certPath,
            String socketPath) {

        host = host != null ? host : fallBackToEnvOrDefault(HOST_ENV_VAR_NAME, DEFAULT_HOST);
        port = port != null ? port : Integer.parseInt(fallBackToEnvOrDefault(PORT_ENV_VAR_NAME, DEFAULT_PORT));
        tls = tls != null ? tls : Boolean.parseBoolean(fallBackToEnvOrDefault(TLS_ENV_VAR_NAME, DEFAULT_TLS));
        certPath = certPath != null ? certPath : fallBackToEnvOrDefault(SERVER_CERT_PATH_ENV_VAR_NAME, null);
        socketPath = socketPath != null ? socketPath : fallBackToEnvOrDefault(SOCKET_PATH_ENV_VAR_NAME, null);

        // we have a socket path specified, build a channel with a unix socket
        if (socketPath != null) {
            return ServiceGrpc.newBlockingStub(NettyChannelBuilder
                    .forAddress(new DomainSocketAddress(socketPath))
                    .eventLoopGroup(new EpollEventLoopGroup())
                    .channelType(EpollDomainSocketChannel.class)
                    .usePlaintext()
                    .build());
        }

        // build a TCP socket
        try {
            NettyChannelBuilder builder = NettyChannelBuilder
                    .forAddress(host, port);

            if (tls) {
                SslContextBuilder sslContext = GrpcSslContexts.forClient();
                if (certPath != null) {
                    sslContext.trustManager(new File(certPath));
                }
                builder.sslContext(sslContext.build());
            } else {
                builder.usePlaintext();
            }

            return ServiceGrpc
                    .newBlockingStub(builder.build());
        } catch (SSLException ssle) {
            SslConfigException sslConfigException = new SslConfigException("Error with SSL configuration.");
            sslConfigException.initCause(ssle);
            throw sslConfigException;
        }
    }

    private static String fallBackToEnvOrDefault(String key, String defaultValue) {
        return System.getenv(key) != null ? System.getenv(key) : defaultValue;
    }
}
