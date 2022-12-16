package dev.openfeature.contrib.providers.flagd;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
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
import lombok.val;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.collections4.map.LRUMap;
import java.util.Collections;
import dev.openfeature.flagd.grpc.Schema.EventStreamRequest;
import dev.openfeature.flagd.grpc.Schema.EventStreamResponse;

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

    static final String LRU_CACHE = "lru";
    static final String DISABLED = "disabled";
    static final String DEFAULT_CACHE = LRU_CACHE;
    static final int DEFAULT_MAX_CACHE_SIZE = 1000;

    static final int DEFAULT_MAX_EVENT_STREAM_RETRIES = 5;
    static final int BASE_EVENT_STREAM_RETRY_BACKOFF_MS = 1000;

    private long deadline = DEFAULT_DEADLINE;
    private ServiceBlockingStub serviceBlockingStub;
    private ServiceStub serviceStub;

    private Boolean cacheEnabled;
    private Boolean eventStreamAlive;
    private Map<String, ProviderEvaluation<Boolean>> booleanCache;
    private Map<String, ProviderEvaluation<String>> stringCache;
    private Map<String, ProviderEvaluation<Double>> doubleCache;
    private Map<String, ProviderEvaluation<Integer>> integerCache;
    private Map<String, ProviderEvaluation<Value>> objectCache;

    private int eventStreamAttempt = 1;
    private int eventStreamRetryBackoff = BASE_EVENT_STREAM_RETRY_BACKOFF_MS;
    private int maxEventStreamRetries = DEFAULT_MAX_EVENT_STREAM_RETRIES;

    private ReadWriteLock lock = new ReentrantReadWriteLock();

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
            fallBackToEnvOrDefault(MAX_EVENT_STREAM_RETRIES_ENV_VAR_NAME, DEFAULT_MAX_EVENT_STREAM_RETRIES)
        );
    }

    /**
     * Create a new FlagdProvider instance.
     *
     * @param socketPath            unix socket path
     * @param cache                 caching implementation to use (lru)
     * @param maxCacheSize          limit of the number of cached values for each type of flag
     * @param maxEventStreamRetries limit of the number of attempts to connect to flagd's event stream,
     *                              on successful connection the attempts are reset
     */
    public FlagdProvider(String socketPath, String cache, int maxCacheSize, int maxEventStreamRetries) {
        this(
            buildServiceBlockingStub(null, null, null, null, socketPath),
            buildServiceStub(null, null, null, null, socketPath),
            cache, maxCacheSize, maxEventStreamRetries
        );
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
            fallBackToEnvOrDefault(MAX_EVENT_STREAM_RETRIES_ENV_VAR_NAME, DEFAULT_MAX_EVENT_STREAM_RETRIES)
        );
    }

    /**
     * Create a new FlagdProvider instance.
     *
     * @param host                  flagd server host, defaults to "localhost"
     * @param port                  flagd server port, defaults to 8013
     * @param tls                   use TLS, defaults to false
     * @param certPath              path for server certificate, defaults to null to, using
     *                              system certs
     * @param cache                 caching implementation to use (lru)
     * @param maxCacheSize          limit of the number of cached values for each type of flag
     * @param maxEventStreamRetries limit of the number of attempts to connect to flagd's event stream,
     *                              on successful connection the attempts are reset
     *
     */
    public FlagdProvider(String host, int port, boolean tls, String certPath, String cache,
        int maxCacheSize, int maxEventStreamRetries) {
        this(
            buildServiceBlockingStub(host, port, tls, certPath, null),
            buildServiceStub(host, port, tls, certPath, null),
            cache, maxCacheSize, maxEventStreamRetries
        );
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
            fallBackToEnvOrDefault(MAX_EVENT_STREAM_RETRIES_ENV_VAR_NAME, DEFAULT_MAX_EVENT_STREAM_RETRIES)
        );
    }

    FlagdProvider(ServiceBlockingStub serviceBlockingStub, ServiceStub serviceStub, String cache,
        int maxCacheSize, int maxEventStreamRetries) {
        this.serviceBlockingStub = serviceBlockingStub;
        this.serviceStub = serviceStub;
        this.eventStreamAlive = false;
        if (cache != null) {
            initCache(cache, maxCacheSize);
        }
        this.maxEventStreamRetries = maxEventStreamRetries;
        this.handleEvents();
    }

    private void initCache(String cache, int maxSize) {
        switch (cache) {
            case LRU_CACHE:
                this.booleanCache = Collections.synchronizedMap(
                    new LRUMap<String, ProviderEvaluation<Boolean>>(maxSize));
                this.stringCache = Collections.synchronizedMap(new LRUMap<String, ProviderEvaluation<String>>(maxSize));
                this.doubleCache = Collections.synchronizedMap(new LRUMap<String, ProviderEvaluation<Double>>(maxSize));
                this.integerCache = Collections.synchronizedMap(
                    new LRUMap<String, ProviderEvaluation<Integer>>(maxSize));
                this.objectCache = Collections.synchronizedMap(new LRUMap<String, ProviderEvaluation<Value>>(maxSize));
                break;
            case DISABLED:
                return;
            default:
                initCache(DEFAULT_CACHE, maxSize);
                return;
        }

        this.cacheEnabled = true;
    }

    private Boolean cacheAvailable() {
        Lock l = this.lock.readLock();
        l.lock();
        Boolean available = this.cacheEnabled && this.eventStreamAlive;
        l.unlock();

        return available;
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
        
        if (this.cacheAvailable()) {
            ProviderEvaluation<Boolean> fromCache = this.booleanCache.get(key);
            if (fromCache != null) {
                fromCache.setReason(CACHED_REASON);
                return fromCache;
            }
        }

        ResolveBooleanRequest request = ResolveBooleanRequest.newBuilder()
                .setFlagKey(key)
                .setContext(this.convertContext(ctx))
                .build();
        ResolveBooleanResponse r = this.serviceBlockingStub.withDeadlineAfter(this.deadline, TimeUnit.MILLISECONDS)
                .resolveBoolean(request);
        ProviderEvaluation<Boolean> result = ProviderEvaluation.<Boolean>builder()
                .value(r.getValue())
                .variant(r.getVariant())
                .reason(r.getReason())
                .build();


        if (this.isEvaluationCacheable(result)) {
            this.booleanCache.put(key, result);
        }
        
        return result;
    }

    @Override
    public ProviderEvaluation<String> getStringEvaluation(String key, String defaultValue,
            EvaluationContext ctx) {
        
        if (this.cacheAvailable()) {
            ProviderEvaluation<String> fromCache = this.stringCache.get(key);
            if (fromCache != null) {
                fromCache.setReason(CACHED_REASON);
                return fromCache;
            }
        }

        ResolveStringRequest request = ResolveStringRequest.newBuilder()
                .setFlagKey(key)
                .setContext(this.convertContext(ctx)).build();
        ResolveStringResponse r = this.serviceBlockingStub.withDeadlineAfter(this.deadline, TimeUnit.MILLISECONDS)
                .resolveString(request);
        ProviderEvaluation<String> result = ProviderEvaluation.<String>builder().value(r.getValue())
                .variant(r.getVariant())
                .reason(r.getReason())
                .build();

        if (this.isEvaluationCacheable(result)) {
            this.stringCache.put(key, result);
        }
        
        return result;
    }

    @Override
    public ProviderEvaluation<Double> getDoubleEvaluation(String key, Double defaultValue,
            EvaluationContext ctx) {

        if (this.cacheAvailable()) {
            ProviderEvaluation<Double> fromCache = this.doubleCache.get(key);
            if (fromCache != null) {
                fromCache.setReason(CACHED_REASON);
                return fromCache;
            }
        }

        ResolveFloatRequest request = ResolveFloatRequest.newBuilder()
                .setFlagKey(key)
                .setContext(this.convertContext(ctx))
                .build();
        ResolveFloatResponse r = this.serviceBlockingStub.withDeadlineAfter(this.deadline, TimeUnit.MILLISECONDS)
                .resolveFloat(request);
        ProviderEvaluation<Double> result = ProviderEvaluation.<Double>builder()
                .value(r.getValue())
                .variant(r.getVariant())
                .reason(r.getReason())
                .build();

        if (this.isEvaluationCacheable(result)) {
            this.doubleCache.put(key, result);
        }
        
        return result;
    }

    @Override
    public ProviderEvaluation<Integer> getIntegerEvaluation(String key, Integer defaultValue,
            EvaluationContext ctx) {
        
        if (this.cacheAvailable()) {
            ProviderEvaluation<Integer> fromCache = this.integerCache.get(key);
            if (fromCache != null) {
                fromCache.setReason(CACHED_REASON);
                return fromCache;
            }
        }
        
        ResolveIntRequest request = ResolveIntRequest.newBuilder()
                .setFlagKey(key)
                .setContext(this.convertContext(ctx))
                .build();
        ResolveIntResponse r = this.serviceBlockingStub.withDeadlineAfter(this.deadline, TimeUnit.MILLISECONDS)
                .resolveInt(request);
        ProviderEvaluation<Integer> result = ProviderEvaluation.<Integer>builder()
                .value((int) r.getValue())
                .variant(r.getVariant())
                .reason(r.getReason())
                .build();

        if (this.isEvaluationCacheable(result)) {
            this.integerCache.put(key, result);
        }
        
        return result;
    }

    @Override
    public ProviderEvaluation<Value> getObjectEvaluation(String key, Value defaultValue,
            EvaluationContext ctx) {
        
        if (this.cacheAvailable()) {
            ProviderEvaluation<Value> fromCache = this.objectCache.get(key);
            if (fromCache != null) {
                fromCache.setReason(CACHED_REASON);
                return fromCache;
            }
        }
        
        ResolveObjectRequest request = ResolveObjectRequest.newBuilder()
                .setFlagKey(key)
                .setContext(this.convertContext(ctx))
                .build();
        ResolveObjectResponse r = this.serviceBlockingStub.withDeadlineAfter(this.deadline, TimeUnit.MILLISECONDS)
                .resolveObject(request);
        ProviderEvaluation<Value> result = ProviderEvaluation.<Value>builder()
                .value(this.convertObjectResponse(r.getValue()))
                .variant(r.getVariant())
                .reason(r.getReason())
                .build();

        if (this.isEvaluationCacheable(result)) {
            this.objectCache.put(key, result);
        }
        
        return result;
    }

    /**
     * Sets how long to wait for an evaluation. 
     *
     * @param deadlineMs time to wait before gRPC call is cancelled. Defaults to 500ms.
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
        StreamObserver<EventStreamResponse> responseObserver = 
            new EventStreamObserver(this.cacheEnabled, this.booleanCache, this.stringCache,
                this.doubleCache, this.integerCache, this.objectCache, this);
        this.serviceStub.eventStream(EventStreamRequest.getDefaultInstance(), responseObserver);
    }

    @Override
    public void setEventStreamAlive(Boolean alive) {
        Lock l = this.lock.writeLock();
        try {
            l.lock();
            this.eventStreamAlive = alive;
            if (alive) {
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

    @Override
    public void restartEventStream() throws Exception {
        this.eventStreamAttempt++;
        if (this.eventStreamAttempt > this.maxEventStreamRetries) {
            // log
            return;
        }
        this.eventStreamRetryBackoff = 2 * this.eventStreamRetryBackoff;
        Thread.sleep(this.eventStreamRetryBackoff);

        this.handleEvents();
    }
}
