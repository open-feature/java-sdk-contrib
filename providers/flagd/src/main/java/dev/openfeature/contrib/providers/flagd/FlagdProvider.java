package dev.openfeature.contrib.providers.flagd;

import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import com.google.protobuf.NullValue;
import dev.openfeature.flagd.grpc.Schema.EventStreamRequest;
import dev.openfeature.flagd.grpc.Schema.EventStreamResponse;
import dev.openfeature.flagd.grpc.Schema.ResolveBooleanRequest;
import dev.openfeature.flagd.grpc.Schema.ResolveFloatRequest;
import dev.openfeature.flagd.grpc.Schema.ResolveIntRequest;
import dev.openfeature.flagd.grpc.Schema.ResolveObjectRequest;
import dev.openfeature.flagd.grpc.Schema.ResolveStringRequest;
import dev.openfeature.flagd.grpc.ServiceGrpc;
import dev.openfeature.flagd.grpc.ServiceGrpc.ServiceBlockingStub;
import dev.openfeature.flagd.grpc.ServiceGrpc.ServiceStub;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.Metadata;
import dev.openfeature.sdk.MutableStructure;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Value;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import io.netty.channel.epoll.EpollDomainSocketChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.handler.ssl.SslContextBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.SSLException;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * OpenFeature provider for flagd.
 */
@Slf4j
public class FlagdProvider implements FeatureProvider, EventStreamCallback {

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
    static final String CACHE_ENV_VAR_NAME = "FLAGD_CACHE";
    static final String MAX_CACHE_SIZE_ENV_VAR_NAME = "FLAGD_MAX_CACHE_SIZE";
    static final String MAX_EVENT_STREAM_RETRIES_ENV_VAR_NAME = "FLAGD_MAX_EVENT_STREAM_RETRIES";

    static final String STATIC_REASON = "STATIC";
    static final String CACHED_REASON = "CACHED";

    static final String FLAG_KEY_FIELD = "flag_key";
    static final String CONTEXT_FIELD = "context";
    static final String VARIANT_FIELD = "variant";
    static final String VALUE_FIELD = "value";
    static final String REASON_FIELD = "reason";

    static final String LRU_CACHE = "lru";
    static final String DISABLED = "disabled";
    static final String DEFAULT_CACHE = LRU_CACHE;
    static final int DEFAULT_MAX_CACHE_SIZE = 1000;

    static final int DEFAULT_MAX_EVENT_STREAM_RETRIES = 5;
    static final int BASE_EVENT_STREAM_RETRY_BACKOFF_MS = 1000;

    private long deadline = DEFAULT_DEADLINE;

    private ServiceBlockingStub serviceBlockingStub;
    private ServiceStub serviceStub;

    private Tracer tracer;

    private Boolean eventStreamAlive;
    private FlagdCache cache;

    private int eventStreamAttempt = 1;
    private int eventStreamRetryBackoff = BASE_EVENT_STREAM_RETRY_BACKOFF_MS;
    private int maxEventStreamRetries;

    private ReadWriteLock lock = new ReentrantReadWriteLock();
    private Object eventStreamAliveSync;

    /**
     * Create a new FlagdProvider instance.
     *
     * @param socketPath unix socket path
     */
    public FlagdProvider(String socketPath) {
        this(
                buildServiceBlockingStub(null, null, null, null, socketPath),
                buildServiceStub(null, null, null, null, socketPath),
                fallBackToEnvOrDefault(CACHE_ENV_VAR_NAME, DEFAULT_CACHE),
                fallBackToEnvOrDefault(MAX_CACHE_SIZE_ENV_VAR_NAME, DEFAULT_MAX_CACHE_SIZE),
                fallBackToEnvOrDefault(MAX_EVENT_STREAM_RETRIES_ENV_VAR_NAME, DEFAULT_MAX_EVENT_STREAM_RETRIES));
    }

    /**
     * Create a new FlagdProvider instance.
     *
     * @param socketPath            unix socket path
     * @param cache                 caching implementation to use (lru)
     * @param maxCacheSize          limit of the number of cached values
     * @param maxEventStreamRetries limit of the number of attempts to connect to
     *                              flagd's event stream,
     *                              on successful connection the attempts are reset
     */
    public FlagdProvider(String socketPath, String cache, int maxCacheSize, int maxEventStreamRetries) {
        this(
                buildServiceBlockingStub(null, null, null, null, socketPath),
                buildServiceStub(null, null, null, null, socketPath),
                cache, maxCacheSize, maxEventStreamRetries);
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
        this(
                buildServiceBlockingStub(host, port, tls, certPath, null),
                buildServiceStub(host, port, tls, certPath, null),
                fallBackToEnvOrDefault(CACHE_ENV_VAR_NAME, DEFAULT_CACHE),
                fallBackToEnvOrDefault(MAX_CACHE_SIZE_ENV_VAR_NAME, DEFAULT_MAX_CACHE_SIZE),
                fallBackToEnvOrDefault(MAX_EVENT_STREAM_RETRIES_ENV_VAR_NAME, DEFAULT_MAX_EVENT_STREAM_RETRIES));
    }

    /**
     * Create a new FlagdProvider instance.
     *
     * @param host                  flagd server host, defaults to "localhost"
     * @param port                  flagd server port, defaults to 8013
     * @param tls                   use TLS, defaults to false
     * @param certPath              path for server certificate, defaults to null
     *                              to, using
     *                              system certs
     * @param cache                 caching implementation to use (lru)
     * @param maxCacheSize          limit of the number of cached values
     * @param maxEventStreamRetries limit of the number of attempts to connect to
     *                              flagd's event stream,
     *                              on successful connection the attempts are reset
     *
     */
    public FlagdProvider(String host, int port, boolean tls, String certPath, String cache,
            int maxCacheSize, int maxEventStreamRetries) {
        this(
                buildServiceBlockingStub(host, port, tls, certPath, null),
                buildServiceStub(host, port, tls, certPath, null),
                cache, maxCacheSize, maxEventStreamRetries);
    }

    /**
     * Create a new FlagdProvider instance.
     */
    public FlagdProvider() {
        this(
                buildServiceBlockingStub(null, null, null, null, null),
                buildServiceStub(null, null, null, null, null),
                fallBackToEnvOrDefault(CACHE_ENV_VAR_NAME, DEFAULT_CACHE),
                fallBackToEnvOrDefault(MAX_CACHE_SIZE_ENV_VAR_NAME, DEFAULT_MAX_CACHE_SIZE),
                fallBackToEnvOrDefault(MAX_EVENT_STREAM_RETRIES_ENV_VAR_NAME, DEFAULT_MAX_EVENT_STREAM_RETRIES));
    }


    /**
     * Create a new FlagdProvider instance with manual telemetry sdk.
     */
    public FlagdProvider(OpenTelemetrySdk telemetrySdk) {
        this(
                buildServiceBlockingStub(telemetrySdk),
                buildServiceStub(null, null, null, null, null),
                fallBackToEnvOrDefault(CACHE_ENV_VAR_NAME, DEFAULT_CACHE),
                fallBackToEnvOrDefault(MAX_CACHE_SIZE_ENV_VAR_NAME, DEFAULT_MAX_CACHE_SIZE),
                fallBackToEnvOrDefault(MAX_EVENT_STREAM_RETRIES_ENV_VAR_NAME, DEFAULT_MAX_EVENT_STREAM_RETRIES));

        this.tracer = telemetrySdk.getTracer("OpenFeature/dev.openfeature.contrib.providers.flagd");
    }

    FlagdProvider(ServiceBlockingStub serviceBlockingStub, ServiceStub serviceStub, String cache,
            int maxCacheSize, int maxEventStreamRetries) {
        this.serviceBlockingStub = serviceBlockingStub;
        this.serviceStub = serviceStub;
        this.eventStreamAlive = false;
        this.cache = new FlagdCache(cache, maxCacheSize);
        this.maxEventStreamRetries = maxEventStreamRetries;
        this.eventStreamAliveSync = new Object();
        this.handleEvents();
    }

    @Override
    public void restartEventStream() throws Exception {
        this.eventStreamAttempt++;
        if (this.eventStreamAttempt > this.maxEventStreamRetries) {
            log.error("failed to connect to event stream, exhausted retries");
            return;
        }
        this.eventStreamRetryBackoff = 2 * this.eventStreamRetryBackoff;
        Thread.sleep(this.eventStreamRetryBackoff);

        this.handleEvents();
    }

    /**
     * Call .wait() on this to block until the event stream is alive.
     * Can be used in instances where the provider being connected to the event
     * stream is a prerequisite
     * to execution (e.g. testing). Not necessary for standard usage.
     *
     * @return eventStreamAliveSync
     */
    public Object getEventStreamAliveSync() {
        return this.eventStreamAliveSync;
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

        ResolveBooleanRequest request = ResolveBooleanRequest.newBuilder().buildPartial();

        return this.resolve(key, ctx, request,
                this.serviceBlockingStub.withDeadlineAfter(this.deadline, TimeUnit.MILLISECONDS)::resolveBoolean, null);
    }

    @Override
    public ProviderEvaluation<String> getStringEvaluation(String key, String defaultValue,
            EvaluationContext ctx) {
        ResolveStringRequest request = ResolveStringRequest.newBuilder().buildPartial();

        return this.resolve(key, ctx, request,
                this.serviceBlockingStub.withDeadlineAfter(this.deadline, TimeUnit.MILLISECONDS)::resolveString, null);
    }

    @Override
    public ProviderEvaluation<Double> getDoubleEvaluation(String key, Double defaultValue,
            EvaluationContext ctx) {
        ResolveFloatRequest request = ResolveFloatRequest.newBuilder().buildPartial();

        return this.resolve(key, ctx, request,
                this.serviceBlockingStub.withDeadlineAfter(this.deadline, TimeUnit.MILLISECONDS)::resolveFloat, null);
    }

    @Override
    public ProviderEvaluation<Integer> getIntegerEvaluation(String key, Integer defaultValue,
            EvaluationContext ctx) {

        ResolveIntRequest request = ResolveIntRequest.newBuilder().buildPartial();

        return this.resolve(key, ctx, request,
                this.serviceBlockingStub.withDeadlineAfter(this.deadline, TimeUnit.MILLISECONDS)::resolveInt,
                (Object value) -> ((Long) value).intValue());
    }

    @Override
    public ProviderEvaluation<Value> getObjectEvaluation(String key, Value defaultValue,
            EvaluationContext ctx) {

        ResolveObjectRequest request = ResolveObjectRequest.newBuilder().buildPartial();

        return this.resolve(key, ctx, request,
                this.serviceBlockingStub.withDeadlineAfter(this.deadline, TimeUnit.MILLISECONDS)::resolveObject,
                (Object value) -> this.convertObjectResponse((com.google.protobuf.Struct) value));
    }

    /**
     * Sets how long to wait for an evaluation.
     *
     * @param deadlineMs time to wait before gRPC call is cancelled. Defaults to
     *                   500ms.
     * @return FlagdProvider
     */
    public FlagdProvider setDeadline(long deadlineMs) {
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
            builder.setNullValue(NullValue.NULL_VALUE);
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

    private static NettyChannelBuilder channelBuilder(String host, Integer port, Boolean tls, String certPath,
            String socketPath) {
        host = host != null ? host : fallBackToEnvOrDefault(HOST_ENV_VAR_NAME, DEFAULT_HOST);
        port = port != null ? port : Integer.parseInt(fallBackToEnvOrDefault(PORT_ENV_VAR_NAME, DEFAULT_PORT));
        tls = tls != null ? tls : Boolean.parseBoolean(fallBackToEnvOrDefault(TLS_ENV_VAR_NAME, DEFAULT_TLS));
        certPath = certPath != null ? certPath : fallBackToEnvOrDefault(SERVER_CERT_PATH_ENV_VAR_NAME, null);
        socketPath = socketPath != null ? socketPath : fallBackToEnvOrDefault(SOCKET_PATH_ENV_VAR_NAME, null);

        // we have a socket path specified, build a channel with a unix socket
        if (socketPath != null) {
            return NettyChannelBuilder
                    .forAddress(new DomainSocketAddress(socketPath))
                    .eventLoopGroup(new EpollEventLoopGroup())
                    .channelType(EpollDomainSocketChannel.class)
                    .usePlaintext();
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

            return builder;
        } catch (SSLException ssle) {
            SslConfigException sslConfigException = new SslConfigException("Error with SSL configuration.");
            sslConfigException.initCause(ssle);
            throw sslConfigException;
        }
    }

    private static ServiceBlockingStub buildServiceBlockingStub(String host, Integer port, Boolean tls, String certPath,
            String socketPath) {
        return ServiceGrpc.newBlockingStub(channelBuilder(host, port, tls, certPath, socketPath).build());
    }

    private static ServiceBlockingStub buildServiceBlockingStub(OpenTelemetrySdk sdk) {
        return ServiceGrpc
                .newBlockingStub(channelBuilder(null, null, null, null, null)
                        .intercept(new FlagdGrpcInterceptor(sdk)).build());
    }

    private static ServiceStub buildServiceStub(String host, Integer port, Boolean tls, String certPath,
            String socketPath) {
        return ServiceGrpc.newStub(channelBuilder(host, port, tls, certPath, socketPath).build());
    }

    private static String fallBackToEnvOrDefault(String key, String defaultValue) {
        return System.getenv(key) != null ? System.getenv(key) : defaultValue;
    }

    private static int fallBackToEnvOrDefault(String key, int defaultValue) {
        try {
            int value = System.getenv(key) != null ? Integer.parseInt(System.getenv(key)) : defaultValue;
            return value;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private void handleEvents() {
        StreamObserver<EventStreamResponse> responseObserver = new EventStreamObserver(this.cache, this);
        this.serviceStub.eventStream(EventStreamRequest.getDefaultInstance(), responseObserver);
    }

    @Override
    public void setEventStreamAlive(Boolean alive) {
        Lock l = this.lock.writeLock();
        try {
            l.lock();
            this.eventStreamAlive = alive;
            if (alive) {
                synchronized (this.eventStreamAliveSync) {
                    this.eventStreamAliveSync.notify(); // notify any waiters that the event stream is alive
                }
                // reset attempts on successful connection
                this.eventStreamAttempt = 1;
                this.eventStreamRetryBackoff = BASE_EVENT_STREAM_RETRY_BACKOFF_MS;
            }
        } finally {
            l.unlock();
        }
    }

    private <T> Boolean isEvaluationCacheable(ProviderEvaluation<T> evaluation) {
        String reason = evaluation.getReason();

        return reason != null && reason.equals(STATIC_REASON) && this.cacheAvailable();
    }

    private Boolean cacheAvailable() {
        Lock l = this.lock.readLock();
        l.lock();
        Boolean available = this.cache.getEnabled() && this.eventStreamAlive;
        l.unlock();

        return available;
    }

    // a generic resolve method that takes a resolverRef and an optional converter lambda to transform the result
    private <ValT extends Object, ReqT extends Message, ResT extends Message> ProviderEvaluation<ValT> resolve(
            String key, EvaluationContext ctx, ReqT request, Function<ReqT, ResT> resolverRef,
            Convert<ValT, Object> converter) {

        // return from cache if available and item is present
        if (this.cacheAvailable()) {
            ProviderEvaluation<? extends Object> fromCache = this.cache.get(key);
            if (fromCache != null) {
                fromCache.setReason(CACHED_REASON);
                return (ProviderEvaluation<ValT>) fromCache;
            }
        }

        // build the gRPC request
        Message req = request.newBuilderForType()
                .setField(this.getFieldDescriptor(request, FLAG_KEY_FIELD), (Object) key)
                .setField(this.getFieldDescriptor(request, CONTEXT_FIELD), this.convertContext(ctx))
                .build();

        // run the referenced resolver method
        final ResT response;

        if (tracer != null) {
            final Span span = tracer.spanBuilder("resolve")
                    .setSpanKind(SpanKind.CLIENT)
                    .startSpan();
            span.setAttribute("feature_flag.key", key);
            try (Scope scope = span.makeCurrent()) {
                response = resolverRef.apply((ReqT) req);
            } finally {
                span.end();
            }
        } else {
            response = resolverRef.apply((ReqT) req);
        }

        // parse the response
        ValT value = converter == null ? this.getField(response, VALUE_FIELD)
                : converter.convert(this.getField(response, VALUE_FIELD));
        ProviderEvaluation<ValT> result = (ProviderEvaluation<ValT>) ProviderEvaluation.<ValT>builder()
                .value(value)
                .variant(this.getField(response, VARIANT_FIELD))
                .reason(this.getField(response, REASON_FIELD))
                .build();

        // cache if cache enabled
        if (this.isEvaluationCacheable(result)) {
            this.cache.put(key, result);
        }

        return (ProviderEvaluation<ValT>) result;
    }

    private <T> T getField(Message message, String name) {
        return (T) message.getField(this.getFieldDescriptor(message, name));
    }

    private FieldDescriptor getFieldDescriptor(Message message, String name) {
        return message.getDescriptorForType().findFieldByName(name);
    }

    // a converter lambda
    @FunctionalInterface
    private interface Convert<OutT extends Object, InT extends Object> {
        OutT convert(InT value);
    }
}
