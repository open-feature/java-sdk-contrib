package dev.openfeature.contrib.providers.multiprovider;

import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.EventProvider;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.Metadata;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Value;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Provider implementation for Multi-provider.
 */
@Slf4j
public class MultiProvider extends EventProvider {

    @Getter
    private static final String NAME = "multiprovider";
    private final Map<String, FeatureProvider> providers;
    private Strategy strategy;
    private String metadataName;

    /**
     * Constructs a MultiProvider with the given list of FeatureProviders, using a default strategy.
     *
     * @param providers the list of FeatureProviders to initialize the MultiProvider with
     */
    public MultiProvider(List<FeatureProvider> providers) {
        this(providers, null);
    }

    /**
     * Constructs a MultiProvider with the given list of FeatureProviders and a strategy.
     *
     * @param providers the list of FeatureProviders to initialize the MultiProvider with
     * @param strategy the strategy
     */
    public MultiProvider(List<FeatureProvider> providers, Strategy strategy) {
        this.providers = new LinkedHashMap<>(providers.size());
        for (FeatureProvider provider: providers) {
            FeatureProvider prevProvider = this.providers.put(provider.getMetadata().getName(), provider);
            if (prevProvider != null) {
                log.warn("duplicated provider name: {}", provider.getMetadata().getName());
            }
        }
        if (strategy != null) {
            this.strategy = strategy;
        } else {
            this.strategy = new FirstMatchStrategy(this.providers);
        }
    }

    /**
     * Initialize the provider.
     * @param evaluationContext evaluation context
     * @throws Exception on error
     */
    @Override
    public void initialize(EvaluationContext evaluationContext) throws Exception {
        JSONObject json = new JSONObject();
        json.put("name", NAME);
        JSONObject providersMetadata = new JSONObject();
        json.put("originalMetadata", providersMetadata);
        for (FeatureProvider provider: providers.values()) {
            provider.initialize(evaluationContext);
            JSONObject providerMetadata = new JSONObject();
            providerMetadata.put("name", provider.getMetadata().getName());
            providersMetadata.put(provider.getMetadata().getName(), providerMetadata);
        }
        metadataName = json.toString();
    }

    @Override
    public Metadata getMetadata() {
        return () -> metadataName;
    }

    @Override
    public ProviderEvaluation<Boolean> getBooleanEvaluation(String key, Boolean defaultValue, EvaluationContext ctx) {
        return strategy.evaluate(key, defaultValue, ctx, p -> p.getBooleanEvaluation(key, defaultValue, ctx));
    }

    @Override
    public ProviderEvaluation<String> getStringEvaluation(String key, String defaultValue, EvaluationContext ctx) {
        return strategy.evaluate(key, defaultValue, ctx, p -> p.getStringEvaluation(key, defaultValue, ctx));
    }

    @Override
    public ProviderEvaluation<Integer> getIntegerEvaluation(String key, Integer defaultValue, EvaluationContext ctx) {
        return strategy.evaluate(key, defaultValue, ctx, p -> p.getIntegerEvaluation(key, defaultValue, ctx));
    }

    @Override
    public ProviderEvaluation<Double> getDoubleEvaluation(String key, Double defaultValue, EvaluationContext ctx) {
        return strategy.evaluate(key, defaultValue, ctx, p -> p.getDoubleEvaluation(key, defaultValue, ctx));
    }

    @Override
    public ProviderEvaluation<Value> getObjectEvaluation(String key, Value defaultValue, EvaluationContext ctx) {
        return strategy.evaluate(key, defaultValue, ctx, p -> p.getObjectEvaluation(key, defaultValue, ctx));
    }

    @Override
    public void shutdown() {
        log.debug("shutdown begin");
        for (FeatureProvider provider: providers.values()) {
            try {
                provider.shutdown();
            } catch (Exception e) {
                log.error("error shutdown provider {}", provider.getMetadata().getName(), e);
            }
        }
        log.debug("shutdown end");
    }

}
