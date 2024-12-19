package dev.openfeature.contrib.providers.flagd.resolver.grpc;

import com.google.protobuf.Message;
import com.google.protobuf.Struct;
import dev.openfeature.contrib.providers.flagd.Config;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.resolver.Resolver;
import dev.openfeature.contrib.providers.flagd.resolver.common.ConnectionEvent;
import dev.openfeature.contrib.providers.flagd.resolver.common.ConnectionState;
import dev.openfeature.contrib.providers.flagd.resolver.grpc.cache.Cache;
import dev.openfeature.contrib.providers.flagd.resolver.grpc.strategy.ResolveFactory;
import dev.openfeature.contrib.providers.flagd.resolver.grpc.strategy.ResolveStrategy;
import dev.openfeature.flagd.grpc.evaluation.Evaluation;
import dev.openfeature.flagd.grpc.evaluation.Evaluation.ResolveBooleanRequest;
import dev.openfeature.flagd.grpc.evaluation.Evaluation.ResolveFloatRequest;
import dev.openfeature.flagd.grpc.evaluation.Evaluation.ResolveIntRequest;
import dev.openfeature.flagd.grpc.evaluation.Evaluation.ResolveObjectRequest;
import dev.openfeature.flagd.grpc.evaluation.Evaluation.ResolveStringRequest;
import dev.openfeature.flagd.grpc.evaluation.ServiceGrpc;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.ImmutableMetadata;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.FlagNotFoundError;
import dev.openfeature.sdk.exceptions.GeneralError;
import dev.openfeature.sdk.exceptions.OpenFeatureError;
import dev.openfeature.sdk.exceptions.ParseError;
import dev.openfeature.sdk.exceptions.TypeMismatchError;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import static dev.openfeature.contrib.providers.flagd.resolver.common.Convert.convertContext;
import static dev.openfeature.contrib.providers.flagd.resolver.common.Convert.convertObjectResponse;
import static dev.openfeature.contrib.providers.flagd.resolver.common.Convert.getField;
import static dev.openfeature.contrib.providers.flagd.resolver.common.Convert.getFieldDescriptor;

/**
 * Resolves flag values using https://buf.build/open-feature/flagd/docs/main:flagd.evaluation.v1.
 * Flags are evaluated remotely.
 */
@SuppressWarnings("PMD.TooManyStaticImports")
@SuppressFBWarnings(justification = "cache needs to be read and write by multiple objects")
public final class GrpcResolver implements Resolver {

    private final GrpcConnector<ServiceGrpc.ServiceStub, ServiceGrpc.ServiceBlockingStub> connector;
    private final Cache cache;
    private final ResolveStrategy strategy;

    /**
     * Resolves flag values using https://buf.build/open-feature/flagd/docs/main:flagd.evaluation.v1.
     * Flags are evaluated remotely.
     *
     * @param options           flagd options
     * @param cache             cache to use
     * @param onConnectionEvent lambda which handles changes in the connection/stream
     */
    public GrpcResolver(final FlagdOptions options, final Cache cache,
                        final Consumer<ConnectionEvent> onConnectionEvent) {
        this.cache = cache;
        this.strategy = ResolveFactory.getStrategy(options);
        this.connector = new GrpcConnector<>(options,
                ServiceGrpc::newStub,
                ServiceGrpc::newBlockingStub,
                onConnectionEvent,
                stub -> stub.eventStream(Evaluation.EventStreamRequest.getDefaultInstance(),
                        new EventStreamObserver(cache,
                                (k, e) -> onConnectionEvent.accept(new ConnectionEvent(ConnectionState.CONNECTED,
                                        e)))));


    }


    /**
     * Initialize Grpc resolver.
     */
    public void init() throws Exception {
        this.connector.initialize();
    }

    /**
     * Shutdown Grpc resolver.
     */
    public void shutdown() throws Exception {
        this.connector.shutdown();
    }

    /**
     * Boolean evaluation from grpc resolver.
     */
    public ProviderEvaluation<Boolean> booleanEvaluation(String key, Boolean defaultValue,
                                                         EvaluationContext ctx) {
        ResolveBooleanRequest request = ResolveBooleanRequest.newBuilder().buildPartial();


        return resolve(key, ctx, request, connector.getResolver()::resolveBoolean,
                null);
    }

    /**
     * String evaluation from grpc resolver.
     */
    public ProviderEvaluation<String> stringEvaluation(String key, String defaultValue,
                                                       EvaluationContext ctx) {
        ResolveStringRequest request = ResolveStringRequest.newBuilder().buildPartial();
        return resolve(key, ctx, request, connector.getResolver()::resolveString,
                null);
    }

    /**
     * Double evaluation from grpc resolver.
     */
    public ProviderEvaluation<Double> doubleEvaluation(String key, Double defaultValue,
                                                       EvaluationContext ctx) {
        ResolveFloatRequest request = ResolveFloatRequest.newBuilder().buildPartial();

        return resolve(key, ctx, request, connector.getResolver()::resolveFloat,
                null);
    }

    /**
     * Integer evaluation from grpc resolver.
     */
    public ProviderEvaluation<Integer> integerEvaluation(String key, Integer defaultValue,
                                                         EvaluationContext ctx) {

        ResolveIntRequest request = ResolveIntRequest.newBuilder().buildPartial();

        return resolve(key, ctx, request, connector.getResolver()::resolveInt,
                (Object value) -> ((Long) value).intValue());
    }

    /**
     * Object evaluation from grpc resolver.
     */
    public ProviderEvaluation<Value> objectEvaluation(String key, Value defaultValue,
                                                      EvaluationContext ctx) {

        ResolveObjectRequest request = ResolveObjectRequest.newBuilder().buildPartial();

        return resolve(key, ctx, request, connector.getResolver()::resolveObject,
                (Object value) -> convertObjectResponse((Struct) value));
    }

    /**
     * A generic resolve method that takes a resolverRef and an optional converter
     * lambda to transform the result.
     */
    private <ValT, ReqT extends Message, ResT extends Message> ProviderEvaluation<ValT> resolve(
            String key, EvaluationContext ctx, ReqT request, Function<ReqT, ResT> resolverRef,
            Convert<ValT, Object> converter) {

        // return from cache if available and item is present
        if (this.cacheAvailable()) {
            ProviderEvaluation<? extends Object> fromCache = this.cache.get(key);
            if (fromCache != null) {
                fromCache.setReason(Config.CACHED_REASON);
                return (ProviderEvaluation<ValT>) fromCache;
            }
        }

        // build the gRPC request
        Message req = request.newBuilderForType()
                .setField(getFieldDescriptor(request, Config.FLAG_KEY_FIELD), key)
                .setField(getFieldDescriptor(request, Config.CONTEXT_FIELD), convertContext(ctx))
                .build();

        final Message response;
        try {
            // run the referenced resolver method
            response = strategy.resolve(resolverRef, req, key);
        } catch (Exception e) {
            OpenFeatureError openFeatureError = mapError(e);
            throw openFeatureError;
        }

        // parse the response
        ValT value = converter == null ? getField(response, Config.VALUE_FIELD)
                : converter.convert(getField(response, Config.VALUE_FIELD));

        // Extract metadata from response
        ImmutableMetadata immutableMetadata = metadataFromResponse(response);

        ProviderEvaluation<ValT> result = ProviderEvaluation.<ValT>builder()
                .value(value)
                .variant(getField(response, Config.VARIANT_FIELD))
                .reason(getField(response, Config.REASON_FIELD))
                .flagMetadata(immutableMetadata)
                .build();

        // cache if cache enabled
        if (this.isEvaluationCacheable(result)) {
            this.cache.put(key, result);
        }

        return result;
    }

    private <T> Boolean isEvaluationCacheable(ProviderEvaluation<T> evaluation) {
        String reason = evaluation.getReason();

        return reason != null && reason.equals(Config.STATIC_REASON) && this.cacheAvailable();
    }

    private Boolean cacheAvailable() {
        return this.cache.getEnabled() && this.connector.isConnected();
    }

    private static ImmutableMetadata metadataFromResponse(Message response) {
        final Object metadata = response.getField(getFieldDescriptor(response, Config.METADATA_FIELD));

        if (!(metadata instanceof Struct)) {
            return ImmutableMetadata.builder().build();
        }

        final Struct struct = (Struct) metadata;

        ImmutableMetadata.ImmutableMetadataBuilder builder = ImmutableMetadata.builder();

        for (Map.Entry<String, com.google.protobuf.Value> entry : struct.getFieldsMap().entrySet()) {
            if (entry.getValue().hasStringValue()) {
                builder.addString(entry.getKey(), entry.getValue().getStringValue());
            } else if (entry.getValue().hasBoolValue()) {
                builder.addBoolean(entry.getKey(), entry.getValue().getBoolValue());
            } else if (entry.getValue().hasNumberValue()) {
                builder.addDouble(entry.getKey(), entry.getValue().getNumberValue());
            }
        }

        return builder.build();
    }

    private OpenFeatureError mapError(Exception e) {
        if (e instanceof StatusRuntimeException) {
            Code code = ((StatusRuntimeException) e).getStatus().getCode();
            switch (code) {
                case DATA_LOSS:
                    return new ParseError(e.getMessage());
                case INVALID_ARGUMENT:
                    return new TypeMismatchError(e.getMessage());
                case NOT_FOUND:
                    return new FlagNotFoundError(e.getMessage());
                default:
                    return new GeneralError(e.getMessage());
            }
        }
        return new GeneralError(e.getMessage());
    }
}
