package dev.openfeature.contrib.providers.flagd.resolver.rpc;

import static dev.openfeature.contrib.providers.flagd.resolver.common.Convert.convertContext;
import static dev.openfeature.contrib.providers.flagd.resolver.common.Convert.convertObjectResponse;
import static dev.openfeature.contrib.providers.flagd.resolver.common.Convert.getField;
import static dev.openfeature.contrib.providers.flagd.resolver.common.Convert.getFieldDescriptor;

import com.google.protobuf.Message;
import dev.openfeature.contrib.providers.flagd.Config;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.resolver.Resolver;
import dev.openfeature.contrib.providers.flagd.resolver.common.ChannelBuilder;
import dev.openfeature.contrib.providers.flagd.resolver.common.ChannelConnector;
import dev.openfeature.contrib.providers.flagd.resolver.common.QueueingStreamObserver;
import dev.openfeature.contrib.providers.flagd.resolver.common.StreamResponseModel;
import dev.openfeature.contrib.providers.flagd.resolver.rpc.cache.Cache;
import dev.openfeature.contrib.providers.flagd.resolver.rpc.strategy.ResolveFactory;
import dev.openfeature.contrib.providers.flagd.resolver.rpc.strategy.ResolveStrategy;
import dev.openfeature.flagd.grpc.evaluation.Evaluation.EventStreamRequest;
import dev.openfeature.flagd.grpc.evaluation.Evaluation.EventStreamResponse;
import dev.openfeature.flagd.grpc.evaluation.Evaluation.ResolveBooleanRequest;
import dev.openfeature.flagd.grpc.evaluation.Evaluation.ResolveFloatRequest;
import dev.openfeature.flagd.grpc.evaluation.Evaluation.ResolveIntRequest;
import dev.openfeature.flagd.grpc.evaluation.Evaluation.ResolveObjectRequest;
import dev.openfeature.flagd.grpc.evaluation.Evaluation.ResolveStringRequest;
import dev.openfeature.flagd.grpc.evaluation.ServiceGrpc;
import dev.openfeature.flagd.grpc.evaluation.ServiceGrpc.ServiceBlockingStub;
import dev.openfeature.flagd.grpc.evaluation.ServiceGrpc.ServiceStub;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.ImmutableMetadata;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.ProviderEvent;
import dev.openfeature.sdk.ProviderEventDetails;
import dev.openfeature.sdk.Structure;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.FlagNotFoundError;
import dev.openfeature.sdk.exceptions.GeneralError;
import dev.openfeature.sdk.exceptions.OpenFeatureError;
import dev.openfeature.sdk.exceptions.ParseError;
import dev.openfeature.sdk.exceptions.TypeMismatchError;
import dev.openfeature.sdk.internal.TriConsumer;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves flag values using https://buf.build/open-feature/flagd/docs/main:flagd.evaluation.v1.
 * Flags are evaluated remotely.
 */
@SuppressWarnings("PMD.TooManyStaticImports")
@SuppressFBWarnings(justification = "cache needs to be read and write by multiple objects")
@Slf4j
public final class RpcResolver implements Resolver {
    private static final int QUEUE_SIZE = 5;
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private final AtomicBoolean successfulConnection = new AtomicBoolean(false);
    private final ChannelConnector connector;
    private final Cache cache;
    private final ResolveStrategy strategy;
    private final FlagdOptions options;
    private final LinkedBlockingQueue<StreamResponseModel<EventStreamResponse>> incomingQueue;
    private final TriConsumer<ProviderEvent, ProviderEventDetails, Structure> onProviderEvent;
    private final ServiceStub stub;
    private final ServiceBlockingStub blockingStub;
    private final List<String> fatalStatusCodes;

    /**
     * Resolves flag values using
     * https://buf.build/open-feature/flagd/docs/main:flagd.evaluation.v1.
     * Flags are evaluated remotely.
     *
     * @param options         flagd options
     * @param cache           cache to use
     * @param onProviderEvent lambda which handles changes in the connection/stream
     */
    public RpcResolver(
            final FlagdOptions options,
            final Cache cache,
            final TriConsumer<ProviderEvent, ProviderEventDetails, Structure> onProviderEvent) {
        this.cache = cache;
        this.strategy = ResolveFactory.getStrategy(options);
        this.options = options;
        incomingQueue = new LinkedBlockingQueue<>(QUEUE_SIZE);
        this.connector = new ChannelConnector(options, ChannelBuilder.nettyChannel(options));
        this.onProviderEvent = onProviderEvent;
        this.stub = ServiceGrpc.newStub(this.connector.getChannel()).withWaitForReady();
        this.blockingStub =
                ServiceGrpc.newBlockingStub(this.connector.getChannel()).withWaitForReady();
        this.fatalStatusCodes = options.getFatalStatusCodes();
    }

    // testing only
    protected RpcResolver(
            final FlagdOptions options,
            final Cache cache,
            final TriConsumer<ProviderEvent, ProviderEventDetails, Structure> onProviderEvent,
            ServiceStub mockStub,
            ServiceBlockingStub mockBlockingStub,
            ChannelConnector connector) {
        this.cache = cache;
        this.strategy = ResolveFactory.getStrategy(options);
        this.options = options;
        incomingQueue = new LinkedBlockingQueue<>(QUEUE_SIZE);
        this.connector = connector;
        this.onProviderEvent = onProviderEvent;
        this.stub = mockStub;
        this.blockingStub = mockBlockingStub;
        this.fatalStatusCodes = options.getFatalStatusCodes();
    }

    /**
     * Initialize RpcResolver resolver.
     */
    public void init() throws Exception {
        Thread listener = new Thread(() -> {
            try {
                observeEventStream();
            } catch (InterruptedException e) {
                log.warn("gRPC event stream interrupted, flag configurations are stale", e);
                Thread.currentThread().interrupt();
            }
        });

        listener.setDaemon(true);
        listener.start();
    }

    /**
     * Shutdown Grpc resolver.
     */
    public void shutdown() throws Exception {
        if (shutdown.getAndSet(true)) {
            return;
        }
        this.connector.shutdown();
    }

    @Override
    public void onError() {
        if (cache != null) {
            cache.clear();
        }
    }

    /**
     * Boolean evaluation from grpc resolver.
     */
    public ProviderEvaluation<Boolean> booleanEvaluation(String key, Boolean defaultValue, EvaluationContext ctx) {
        ResolveBooleanRequest request = ResolveBooleanRequest.newBuilder().buildPartial();

        return resolve(key, ctx, request, getBlockingStub()::resolveBoolean, null);
    }

    /**
     * String evaluation from grpc resolver.
     */
    public ProviderEvaluation<String> stringEvaluation(String key, String defaultValue, EvaluationContext ctx) {
        ResolveStringRequest request = ResolveStringRequest.newBuilder().buildPartial();
        return resolve(key, ctx, request, getBlockingStub()::resolveString, null);
    }

    /**
     * Double evaluation from grpc resolver.
     */
    public ProviderEvaluation<Double> doubleEvaluation(String key, Double defaultValue, EvaluationContext ctx) {
        ResolveFloatRequest request = ResolveFloatRequest.newBuilder().buildPartial();

        return resolve(key, ctx, request, getBlockingStub()::resolveFloat, null);
    }

    /**
     * Integer evaluation from grpc resolver.
     */
    public ProviderEvaluation<Integer> integerEvaluation(String key, Integer defaultValue, EvaluationContext ctx) {

        ResolveIntRequest request = ResolveIntRequest.newBuilder().buildPartial();

        return resolve(key, ctx, request, getBlockingStub()::resolveInt, (Object value) -> ((Long) value).intValue());
    }

    private ServiceGrpc.ServiceBlockingStub getBlockingStub() {
        ServiceBlockingStub localStub = blockingStub;

        if (options.getDeadline() > 0) {
            localStub = localStub.withDeadlineAfter(options.getDeadline(), TimeUnit.MILLISECONDS);
        }

        return localStub;
    }

    /**
     * Object evaluation from grpc resolver.
     */
    public ProviderEvaluation<Value> objectEvaluation(String key, Value defaultValue, EvaluationContext ctx) {

        ResolveObjectRequest request = ResolveObjectRequest.newBuilder().buildPartial();

        return resolve(
                key,
                ctx,
                request,
                getBlockingStub()::resolveObject,
                (Object value) -> convertObjectResponse((com.google.protobuf.Struct) value));
    }

    /**
     * A generic resolve method that takes a resolverRef and an optional converter
     * lambda to transform the result.
     */
    private <ValT, ReqT extends Message, ResT extends Message> ProviderEvaluation<ValT> resolve(
            String key,
            EvaluationContext ctx,
            ReqT request,
            Function<ReqT, ResT> resolverRef,
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
        ValT value = converter == null
                ? getField(response, Config.VALUE_FIELD)
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

    private <T> boolean isEvaluationCacheable(ProviderEvaluation<T> evaluation) {
        String reason = evaluation.getReason();

        return reason != null && reason.equals(Config.STATIC_REASON) && this.cacheAvailable();
    }

    private boolean cacheAvailable() {
        return this.cache.isEnabled();
    }

    private static ImmutableMetadata metadataFromResponse(Message response) {
        final Object metadata = response.getField(getFieldDescriptor(response, Config.METADATA_FIELD));

        if (!(metadata instanceof com.google.protobuf.Struct)) {
            return ImmutableMetadata.builder().build();
        }

        final com.google.protobuf.Struct struct = (com.google.protobuf.Struct) metadata;

        ImmutableMetadata.ImmutableMetadataBuilder builder = ImmutableMetadata.builder();

        for (Map.Entry<String, com.google.protobuf.Value> entry :
                struct.getFieldsMap().entrySet()) {
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

    private void restartStream() {
        ServiceStub localStub = stub; // don't mutate the stub
        if (options.getStreamDeadlineMs() > 0) {
            localStub = localStub.withDeadlineAfter(options.getStreamDeadlineMs(), TimeUnit.MILLISECONDS);
        }

        localStub.eventStream(
                EventStreamRequest.getDefaultInstance(),
                new QueueingStreamObserver<EventStreamResponse>(incomingQueue));
    }

    /** Contains blocking calls, to be used concurrently. */
    private void observeEventStream() throws InterruptedException {

        log.info("Initializing event stream observer");

        // outer loop for re-issuing the stream request
        // "waitForReady" on the channel, plus our retry policy slow this loop down in error conditions
        while (!shutdown.get()) {

            log.debug("Initializing event stream request");
            restartStream();
            // inner loop for handling messages
            while (!shutdown.get()) {
                final StreamResponseModel<EventStreamResponse> taken = incomingQueue.take();
                if (taken.isComplete()) {
                    log.debug("Event stream completed, will reconnect");
                    this.handleErrorOrComplete(false);
                    // The stream is complete, we still try to reconnect
                    break;
                }

                Throwable streamException = taken.getError();
                if (streamException != null) {
                    if (streamException instanceof StatusRuntimeException
                            && fatalStatusCodes.contains(((StatusRuntimeException) streamException)
                                    .getStatus()
                                    .getCode()
                                    .name())
                            && !successfulConnection.get()) {
                        log.debug(
                                "Fatal error code received: {}",
                                ((StatusRuntimeException) streamException)
                                        .getStatus()
                                        .getCode());
                        this.handleErrorOrComplete(true);
                    } else {
                        log.debug(
                                "Exception in event stream connection, streamException {}, will reconnect",
                                streamException);
                        this.handleErrorOrComplete(false);
                    }
                    break;
                }

                successfulConnection.set(true);
                final EventStreamResponse response = taken.getResponse();
                log.debug("Got stream response: {}", response);

                switch (response.getType()) {
                    case Constants.CONFIGURATION_CHANGE:
                        this.handleConfigurationChangeEvent(response);
                        break;
                    case Constants.PROVIDER_READY:
                        this.handleProviderReadyEvent();
                        break;
                    default:
                        log.debug("Unhandled event type {}", response.getType());
                }
            }
        }

        log.info("Shutdown invoked, exiting event stream listener");
    }

    /**
     * Handles configuration change events by updating the cache and notifying listeners about changed flags.
     *
     * @param value the event stream response containing configuration change data
     */
    private void handleConfigurationChangeEvent(EventStreamResponse value) {
        List<String> changedFlags = new ArrayList<>();

        Map<String, com.google.protobuf.Value> data = value.getData().getFieldsMap();
        com.google.protobuf.Value flagsValue = data.get(Constants.FLAGS_KEY);
        if (flagsValue != null) {
            Map<String, com.google.protobuf.Value> flags =
                    flagsValue.getStructValue().getFieldsMap();
            changedFlags.addAll(flags.keySet());
        }

        log.debug("Emitting provider change event");
        if (this.cache != null) {
            changedFlags.forEach(this.cache::remove);
        }

        onProviderEvent.accept(
                ProviderEvent.PROVIDER_CONFIGURATION_CHANGED,
                ProviderEventDetails.builder().flagsChanged(changedFlags).build(),
                null);
    }

    /**
     * Handles provider readiness events by clearing the cache (if enabled) and notifying listeners of readiness.
     */
    private void handleProviderReadyEvent() {
        log.debug("Emitting provider ready event");
        onProviderEvent.accept(ProviderEvent.PROVIDER_READY, null, null);
    }

    /**
     * Handles provider error events by clearing the cache (if enabled) and notifying listeners of the error.
     */
    private void handleErrorOrComplete(boolean fatal) {
        log.debug("Emitting provider error event");
        ErrorCode errorCode = fatal ? ErrorCode.PROVIDER_FATAL : ErrorCode.GENERAL;
        var details = ProviderEventDetails.builder().errorCode(errorCode).build();

        // complete is an error, logically...even if the server went down gracefully we need to reconnect.
        onProviderEvent.accept(ProviderEvent.PROVIDER_ERROR, details, null);
    }
}
