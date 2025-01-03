package dev.openfeature.contrib.providers.configcat;

import com.configcat.ConfigCatClient;
import com.configcat.EvaluationDetails;
import com.configcat.User;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.EventProvider;
import dev.openfeature.sdk.Metadata;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.ProviderEventDetails;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.GeneralError;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/** Provider implementation for ConfigCat. */
@Slf4j
public class ConfigCatProvider extends EventProvider {

    @Getter
    private static final String NAME = "ConfigCat";

    public static final String PROVIDER_NOT_YET_INITIALIZED = "provider not yet initialized";
    public static final String UNKNOWN_ERROR = "unknown error";

    private ConfigCatProviderConfig configCatProviderConfig;

    @Getter
    private ConfigCatClient configCatClient;

    private AtomicBoolean isInitialized = new AtomicBoolean(false);

    /**
     * Constructor.
     *
     * @param configCatProviderConfig configCatProvider Config
     */
    public ConfigCatProvider(ConfigCatProviderConfig configCatProviderConfig) {
        this.configCatProviderConfig = configCatProviderConfig;
    }

    /**
     * Initialize the provider.
     *
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
        configCatClient =
                ConfigCatClient.get(configCatProviderConfig.getSdkKey(), configCatProviderConfig.getOptions());
        configCatProviderConfig.postInit();
        log.info("finished initializing provider");

        configCatClient.getHooks().addOnClientReady(() -> {
            ProviderEventDetails providerEventDetails =
                    ProviderEventDetails.builder().message("provider ready").build();
            emitProviderReady(providerEventDetails);
        });

        configCatClient.getHooks().addOnConfigChanged(map -> {
            ProviderEventDetails providerEventDetails = ProviderEventDetails.builder()
                    .flagsChanged(new ArrayList<>(map.keySet()))
                    .message("config changed")
                    .build();
            emitProviderConfigurationChanged(providerEventDetails);
        });

        configCatClient.getHooks().addOnError(errorMessage -> {
            ProviderEventDetails providerEventDetails =
                    ProviderEventDetails.builder().message(errorMessage).build();
            emitProviderError(providerEventDetails);
        });
    }

    @Override
    public Metadata getMetadata() {
        return () -> NAME;
    }

    @Override
    public ProviderEvaluation<Boolean> getBooleanEvaluation(String key, Boolean defaultValue, EvaluationContext ctx) {
        return getEvaluation(Boolean.class, key, defaultValue, ctx);
    }

    @Override
    public ProviderEvaluation<String> getStringEvaluation(String key, String defaultValue, EvaluationContext ctx) {
        return getEvaluation(String.class, key, defaultValue, ctx);
    }

    @Override
    public ProviderEvaluation<Integer> getIntegerEvaluation(String key, Integer defaultValue, EvaluationContext ctx) {
        return getEvaluation(Integer.class, key, defaultValue, ctx);
    }

    @Override
    public ProviderEvaluation<Double> getDoubleEvaluation(String key, Double defaultValue, EvaluationContext ctx) {
        return getEvaluation(Double.class, key, defaultValue, ctx);
    }

    @SneakyThrows
    @Override
    public ProviderEvaluation<Value> getObjectEvaluation(String key, Value defaultValue, EvaluationContext ctx) {
        return getEvaluation(Value.class, key, defaultValue, ctx);
    }

    private <T> ProviderEvaluation<T> getEvaluation(
            Class<T> classOfT, String key, T defaultValue, EvaluationContext ctx) {
        User user = ctx == null ? null : ContextTransformer.transform(ctx);
        EvaluationDetails<T> evaluationDetails;
        T evaluatedValue;
        if (classOfT.isAssignableFrom(Value.class)) {
            String defaultValueStr = defaultValue == null ? null : ((Value) defaultValue).asString();
            evaluationDetails =
                    (EvaluationDetails<T>) configCatClient.getValueDetails(String.class, key, user, defaultValueStr);
            evaluatedValue =
                    evaluationDetails.getValue() == null ? null : (T) Value.objectToValue(evaluationDetails.getValue());
        } else {
            evaluationDetails = configCatClient.getValueDetails(classOfT, key, user, defaultValue);
            evaluatedValue = evaluationDetails.getValue();
        }
        return ProviderEvaluation.<T>builder()
                .value(evaluatedValue)
                .variant(evaluationDetails.getVariationId())
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
    }
}
