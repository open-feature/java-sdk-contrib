package dev.openfeature.contrib.providers.flagd;

import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import com.google.protobuf.NullValue;
import dev.openfeature.contrib.providers.flagd.grpc.GrpcConnector;
import dev.openfeature.contrib.providers.flagd.strategy.ResolveStrategy;
import dev.openfeature.contrib.providers.flagd.strategy.SimpleResolving;
import dev.openfeature.contrib.providers.flagd.strategy.TracedResolving;
import dev.openfeature.flagd.grpc.Schema.ResolveBooleanRequest;
import dev.openfeature.flagd.grpc.Schema.ResolveFloatRequest;
import dev.openfeature.flagd.grpc.Schema.ResolveIntRequest;
import dev.openfeature.flagd.grpc.Schema.ResolveObjectRequest;
import dev.openfeature.flagd.grpc.Schema.ResolveStringRequest;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.EventProvider;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.Metadata;
import dev.openfeature.sdk.MutableStructure;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.ProviderEventDetails;
import dev.openfeature.sdk.ProviderState;
import dev.openfeature.sdk.Value;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.stream.Collectors;

import static dev.openfeature.contrib.providers.flagd.Config.CACHED_REASON;
import static dev.openfeature.contrib.providers.flagd.Config.CONTEXT_FIELD;
import static dev.openfeature.contrib.providers.flagd.Config.FLAG_KEY_FIELD;
import static dev.openfeature.contrib.providers.flagd.Config.REASON_FIELD;
import static dev.openfeature.contrib.providers.flagd.Config.STATIC_REASON;
import static dev.openfeature.contrib.providers.flagd.Config.VALUE_FIELD;
import static dev.openfeature.contrib.providers.flagd.Config.VARIANT_FIELD;

/**
 * OpenFeature provider for flagd.
 */
@Slf4j
@SuppressWarnings("PMD.TooManyStaticImports")
public class FlagdProvider extends EventProvider implements FeatureProvider {
    private static final String FLAGD_PROVIDER = "flagD Provider";

    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private final FlagdCache cache;
    private final ResolveStrategy strategy;
    private final GrpcConnector grpc;


    private ProviderState state = ProviderState.NOT_READY;

    /**
     * Create a new FlagdProvider instance with default options.
     */
    public FlagdProvider() {
        this(FlagdOptions.builder().build());
    }

    /**
     * Create a new FlagdProvider instance with customized options.
     *
     * @param options {@link FlagdOptions} with
     */
    public FlagdProvider(final FlagdOptions options) {
        this.strategy = options.getOpenTelemetry() == null
                ? new SimpleResolving()
                : new TracedResolving(options.getOpenTelemetry());
        this.cache = new FlagdCache(options.getCacheType(), options.getMaxCacheSize());
        this.grpc = new GrpcConnector(options, this.cache, this::setState);
    }

//    FlagdProvider(ServiceBlockingStub serviceBlockingStub, ServiceStub serviceStub, String cache, int maxCacheSize,
//                  int maxEventStreamRetries) {
//        this.serviceBlockingStub = serviceBlockingStub;
//        this.serviceStub = serviceStub;
//        this.strategy = new SimpleResolving();
//
//        this.maxEventStreamRetries = maxEventStreamRetries;
//        this.cache = new FlagdCache(cache, maxCacheSize);
//    }

    @Override
    public void initialize(EvaluationContext evaluationContext) throws RuntimeException {
        this.grpc.initialize(evaluationContext);
    }

    @Override
    public void shutdown() {
        try {
            this.grpc.shutdown();
        } catch (Exception e) {
            log.error("Error during shutdown {}", FLAGD_PROVIDER, e);
        }
    }

    @Override
    public ProviderState getState() {
        Lock l = this.lock.readLock();
        try {
            l.lock();
            return this.state;
        } finally {
            l.unlock();
        }
    }

    private void setState(ProviderState newState) {
        ProviderState oldState;
        Lock l = this.lock.writeLock();
        try {
            l.lock();
            oldState = this.state;
            this.state = newState;
        } finally {
            l.unlock();
        }
        this.handleStateChange(oldState, newState);
    }

    private void handleStateChange(ProviderState oldState, ProviderState newState) {
        // we are connected, reset the gRPC retry logic
        if (ProviderState.READY.equals(newState)) {
            this.grpc.resetRetryConnection();
        }
        // we got initialized
        if (ProviderState.NOT_READY.equals(oldState) && ProviderState.READY.equals(newState)) {
            // nothing to do, the SDK emits the events
            log.debug("Init completed");
            return;
        }
        // configuration changed
        if (ProviderState.READY.equals(oldState) && ProviderState.READY.equals(newState)) {
            log.debug("Configuration changed");
            ProviderEventDetails details = ProviderEventDetails.builder().message("configuration changed").build();
            this.emitProviderConfigurationChanged(details);
            return;
        }
        // there was an error
        if (ProviderState.READY.equals(oldState) && ProviderState.ERROR.equals(newState)) {
            log.debug("There has been an error");
            ProviderEventDetails details = ProviderEventDetails.builder().message("there has been an error").build();
            this.emitProviderError(details);
            return;
        }
        // we recover from an error
        if (ProviderState.ERROR.equals(oldState) && ProviderState.READY.equals(newState)) {
            log.debug("Recovered from error");
            ProviderEventDetails details = ProviderEventDetails.builder().message("recovered from error").build();
            this.emitProviderReady(details);
            this.emitProviderConfigurationChanged(details);
        }
    }


    @Override
    public Metadata getMetadata() {
        return () -> FLAGD_PROVIDER;
    }

    @Override
    public ProviderEvaluation<Boolean> getBooleanEvaluation(String key, Boolean defaultValue,
                                                            EvaluationContext ctx) {

        ResolveBooleanRequest request = ResolveBooleanRequest.newBuilder().buildPartial();

        return this.resolve(key, ctx, request,
                this.grpc.getResolver()::resolveBoolean, null);
    }

    @Override
    public ProviderEvaluation<String> getStringEvaluation(String key, String defaultValue,
                                                          EvaluationContext ctx) {
        ResolveStringRequest request = ResolveStringRequest.newBuilder().buildPartial();

        return this.resolve(key, ctx, request,
                this.grpc.getResolver()::resolveString, null);
    }

    @Override
    public ProviderEvaluation<Double> getDoubleEvaluation(String key, Double defaultValue,
                                                          EvaluationContext ctx) {
        ResolveFloatRequest request = ResolveFloatRequest.newBuilder().buildPartial();

        return this.resolve(key, ctx, request,
                this.grpc.getResolver()::resolveFloat, null);
    }

    @Override
    public ProviderEvaluation<Integer> getIntegerEvaluation(String key, Integer defaultValue,
                                                            EvaluationContext ctx) {

        ResolveIntRequest request = ResolveIntRequest.newBuilder().buildPartial();

        return this.resolve(key, ctx, request,
                this.grpc.getResolver()::resolveInt,
                (Object value) -> ((Long) value).intValue());
    }

    @Override
    public ProviderEvaluation<Value> getObjectEvaluation(String key, Value defaultValue,
                                                         EvaluationContext ctx) {

        ResolveObjectRequest request = ResolveObjectRequest.newBuilder().buildPartial();

        return this.resolve(key, ctx, request,
                this.grpc.getResolver()::resolveObject,
                (Object value) -> this.convertObjectResponse((com.google.protobuf.Struct) value));
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

    private <T> Boolean isEvaluationCacheable(ProviderEvaluation<T> evaluation) {
        String reason = evaluation.getReason();

        return reason != null && reason.equals(STATIC_REASON) && this.cacheAvailable();
    }

    private Boolean cacheAvailable() {
        Lock l = this.lock.readLock();
        l.lock();
        Boolean available = this.cache.getEnabled() && this.state == ProviderState.READY;
        l.unlock();

        return available;
    }

    /**
     * A generic resolve method that takes a resolverRef and an optional converter lambda to transform the result.
     */
    private <ValT, ReqT extends Message, ResT extends Message> ProviderEvaluation<ValT> resolve(
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
                .setField(getFieldDescriptor(request, FLAG_KEY_FIELD), key)
                .setField(getFieldDescriptor(request, CONTEXT_FIELD), this.convertContext(ctx))
                .build();

        // run the referenced resolver method
        Message response = strategy.resolve(resolverRef, req, key);

        // parse the response
        ValT value = converter == null ? getField(response, VALUE_FIELD)
                : converter.convert(getField(response, VALUE_FIELD));
        ProviderEvaluation<ValT> result = ProviderEvaluation.<ValT>builder()
                .value(value)
                .variant(getField(response, VARIANT_FIELD))
                .reason(getField(response, REASON_FIELD))
                .build();

        // cache if cache enabled
        if (this.isEvaluationCacheable(result)) {
            this.cache.put(key, result);
        }

        return result;
    }

    private static <T> T getField(Message message, String name) {
        return (T) message.getField(getFieldDescriptor(message, name));
    }

    private static FieldDescriptor getFieldDescriptor(Message message, String name) {
        return message.getDescriptorForType().findFieldByName(name);
    }

    /**
     * A converter lambda.
     */
    @FunctionalInterface
    private interface Convert<OutT extends Object, InT extends Object> {
        OutT convert(InT value);
    }
}
