package dev.openfeature.contrib.providers.multiprovider;

import dev.openfeature.sdk.*;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Provider implementation for Multi-provider.
 */
@Slf4j
public class MultiProvider extends EventProvider {

    @Getter
    private static final String NAME = "Multi-Provider";
    private static String metadataName;
    private Map<String, FeatureProvider> providers;
    private Strategy strategy;

    public MultiProvider(List<FeatureProvider> providers) {
        this(providers, null);
    }

    /**
     * constructor.
     * @param providers providers list
     */
    public MultiProvider(List<FeatureProvider> providers, Strategy strategy) {
        this.providers = new LinkedHashMap<>(providers.size());
        for (FeatureProvider provider: providers) {
            FeatureProvider prevProvider = this.providers.put(provider.getMetadata().getName(), provider);
            if (prevProvider != null) {
                throw new IllegalArgumentException("duplicated provider name: " + provider.getMetadata().getName());
            }
        }
        List<String> providerNames = new ArrayList<>(providers.size());
        for (FeatureProvider provider: providers) {
            providerNames.add(provider.getMetadata().getName());
        }
        metadataName = NAME + "[" + StringUtils.join(providerNames, ",") + "]";
        if (strategy == null) {
            this.strategy = new FirstMatchStrategy(this.providers);
        } else {
            this.strategy = strategy;
        }
    }

    /**
     * Initialize the provider.
     * @param evaluationContext evaluation context
     * @throws Exception on error
     */
    @Override
    public void initialize(EvaluationContext evaluationContext) throws Exception {
        for (FeatureProvider provider: providers.values()) {
            provider.initialize(evaluationContext);
        }
    }

    @Override
    public Metadata getMetadata() {
        return () -> metadataName;
    }

    @Override
    @SuppressFBWarnings(value = {"NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE"}, justification = "reason can be null")
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

    @SneakyThrows
    @Override
    public void shutdown() {
        log.debug("shutdown begin");
        for (FeatureProvider provider: providers.values()) {
            try {
                provider.shutdown();
            } catch (Exception e) {
                log.error("error shutdown provider", provider.getMetadata().getName());
            }
        }
        log.debug("shutdown end");
    }

}
