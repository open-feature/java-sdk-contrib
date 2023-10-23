package dev.openfeature.contrib.providers.configcat;

import com.configcat.ConfigCatClient;
import com.configcat.EvaluationDetails;
import com.configcat.User;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.EventProvider;
import dev.openfeature.sdk.Metadata;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.ProviderEventDetails;
import dev.openfeature.sdk.ProviderState;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.GeneralError;
import dev.openfeature.sdk.exceptions.ProviderNotReadyError;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Provider implementation for ConfigCat.
 */
@Slf4j
public class ConfigCatProvider extends EventProvider {

    @Getter
    private static final String NAME = "ConfigCat";
    public static final String NOT_IMPLEMENTED =
        "Not implemented - provider does not support this type. Only boolean is supported.";

    public static final String PROVIDER_NOT_YET_INITIALIZED = "provider not yet initialized";
    public static final String UNKNOWN_ERROR = "unknown error";

    @Getter(AccessLevel.PROTECTED)
    private ConfigCatProviderConfig configCatProviderConfig;

    @Setter(AccessLevel.PROTECTED)
    @Getter
    private ConfigCatClient configCatClient;

    @Setter(AccessLevel.PROTECTED)
    @Getter
    private ProviderState state = ProviderState.NOT_READY;

    private AtomicBoolean isInitialized = new AtomicBoolean(false);

    /**
     * Constructor.
     * @param configCatProviderConfig configCatProvider Config
     */
    public ConfigCatProvider(ConfigCatProviderConfig configCatProviderConfig) {
        this.configCatProviderConfig = configCatProviderConfig;
    }

    /**
     * Initialize the provider.
     * @param evaluationContext evaluation context
     * @throws Exception on error
     */
    @Override
    public void initialize(EvaluationContext evaluationContext) throws Exception {
        boolean initialized = isInitialized.getAndSet(true);
        if (initialized) {
            throw new GeneralError("already initialized");
        }
        super.initialize(evaluationContext);
        configCatClient = ConfigCatClient.get(configCatProviderConfig.getSdkKey(),
            configCatProviderConfig.getOptions());
        configCatProviderConfig.postInit();
        state = ProviderState.READY;
        log.info("finished initializing provider, state: {}", state);

        configCatClient.getHooks().addOnClientReady(() -> {
            state = ProviderState.READY;
            ProviderEventDetails providerEventDetails = ProviderEventDetails.builder()
                .message("provider ready")
                .build();
            emitProviderReady(providerEventDetails);
        });

        configCatClient.getHooks().addOnConfigChanged(map -> {
            ProviderEventDetails providerEventDetails = ProviderEventDetails.builder()
                .flagsChanged(new ArrayList<>(map.keySet()))
                .message("config changed")
                .build();
            emitProviderReady(providerEventDetails);
        });

        configCatClient.getHooks().addOnError(errorMessage -> {
            ProviderEventDetails providerEventDetails = ProviderEventDetails.builder()
                .message(errorMessage)
                .build();
            emitProviderError(providerEventDetails);
        });
    }

    @Override
    public Metadata getMetadata() {
        return () -> NAME;
    }

    @Override
    public ProviderEvaluation<Boolean> getBooleanEvaluation(String key, Boolean defaultValue, EvaluationContext ctx) {
        if (!ProviderState.READY.equals(state)) {
            if (ProviderState.NOT_READY.equals(state)) {
                throw new ProviderNotReadyError(PROVIDER_NOT_YET_INITIALIZED);
            }
            throw new GeneralError(UNKNOWN_ERROR);
        }

        User user = ctx == null ? null : dev.openfeature.contrib.providers.configcat.ContextTransformer.transform(ctx);
        EvaluationDetails<Boolean> evaluationDetails = configCatClient
            .getValueDetails(Boolean.class, key, user, defaultValue);
        return ProviderEvaluation.<Boolean>builder()
            .value(evaluationDetails.getValue())
            .variant(evaluationDetails.getVariationId())
            .errorMessage(evaluationDetails.getError())
            .build();
    }

    @Override
    public ProviderEvaluation<String> getStringEvaluation(String key, String defaultValue, EvaluationContext ctx) {
        if (!ProviderState.READY.equals(state)) {
            if (ProviderState.NOT_READY.equals(state)) {
                throw new ProviderNotReadyError(PROVIDER_NOT_YET_INITIALIZED);
            }
            throw new GeneralError(UNKNOWN_ERROR);
        }

        User user = ctx == null ? null : dev.openfeature.contrib.providers.configcat.ContextTransformer.transform(ctx);
        EvaluationDetails<String> evaluationDetails = configCatClient
            .getValueDetails(String.class, key, user, defaultValue);
        return ProviderEvaluation.<String>builder()
            .value(evaluationDetails.getValue())
            .variant(evaluationDetails.getVariationId())
            .errorMessage(evaluationDetails.getError())
            .build();
    }

    @Override
    public ProviderEvaluation<Integer> getIntegerEvaluation(String key, Integer defaultValue, EvaluationContext ctx) {
        if (!ProviderState.READY.equals(state)) {
            if (ProviderState.NOT_READY.equals(state)) {
                throw new ProviderNotReadyError(PROVIDER_NOT_YET_INITIALIZED);
            }
            throw new GeneralError(UNKNOWN_ERROR);
        }

        User user = ctx == null ? null : dev.openfeature.contrib.providers.configcat.ContextTransformer.transform(ctx);
        EvaluationDetails<Integer> evaluationDetails = configCatClient
            .getValueDetails(Integer.class, key, user, defaultValue);
        return ProviderEvaluation.<Integer>builder()
            .value(evaluationDetails.getValue())
            .variant(evaluationDetails.getVariationId())
            .errorMessage(evaluationDetails.getError())
            .build();
    }

    @Override
    public ProviderEvaluation<Double> getDoubleEvaluation(String key, Double defaultValue, EvaluationContext ctx) {
        if (!ProviderState.READY.equals(state)) {
            if (ProviderState.NOT_READY.equals(state)) {
                throw new ProviderNotReadyError(PROVIDER_NOT_YET_INITIALIZED);
            }
            throw new GeneralError(UNKNOWN_ERROR);
        }

        User user = ctx == null ? null : dev.openfeature.contrib.providers.configcat.ContextTransformer.transform(ctx);
        EvaluationDetails<Double> evaluationDetails = configCatClient
            .getValueDetails(Double.class, key, user, defaultValue);
        return ProviderEvaluation.<Double>builder()
            .value(evaluationDetails.getValue())
            .variant(evaluationDetails.getVariationId())
            .errorMessage(evaluationDetails.getError())
            .build();
    }

    @SneakyThrows
    @Override
    public ProviderEvaluation<Value> getObjectEvaluation(String key, Value defaultValue, EvaluationContext ctx) {
        if (!ProviderState.READY.equals(state)) {
            if (ProviderState.NOT_READY.equals(state)) {
                throw new ProviderNotReadyError(PROVIDER_NOT_YET_INITIALIZED);
            }
            throw new GeneralError(UNKNOWN_ERROR);
        }

        User user = ctx == null ? null : dev.openfeature.contrib.providers.configcat.ContextTransformer.transform(ctx);
        EvaluationDetails<String> evaluationDetails = configCatClient
            .getValueDetails(String.class, key, user, defaultValue.asString());
        return ProviderEvaluation.<Value>builder()
            .value(Value.objectToValue(evaluationDetails.getValue()))
            .variant(evaluationDetails.getVariationId())
            .errorMessage(evaluationDetails.getError())
            .build();
    }

    @SneakyThrows
    @Override
    public void shutdown() {
        super.shutdown();
        log.info("shutdown");
        if (configCatClient != null) {
            configCatClient.close();
        }
        state = ProviderState.NOT_READY;
    }
}
