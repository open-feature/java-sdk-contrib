package dev.openfeature.contrib.providers.gcp;

import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.Metadata;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Reason;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.GeneralError;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;

@Slf4j
abstract class AbstractGcpProvider<C> implements FeatureProvider {

    protected final GcpProviderOptions options;
    protected C client;
    protected FlagCache cache;
    private AtomicBoolean isInitialized = new AtomicBoolean(false);

    AbstractGcpProvider(GcpProviderOptions options) {
        this.options = options;
    }

    AbstractGcpProvider(GcpProviderOptions options, C client) {
        this.options = options;
        this.client = client;
    }

    @Override
    public Metadata getMetadata() {
        return () -> getProviderName();
    }

    @Override
    public void initialize(EvaluationContext evaluationContext) throws Exception {
        boolean initialized = isInitialized.getAndSet(true);
        if (initialized) {
            throw new GeneralError("already initialized");
        }

        options.validate();
        if (client == null) {
            createClient();
        }
        cache = new FlagCache(options.getCacheExpiry(), options.getCacheMaxSize());
        log.info("{} initialized for project '{}'", getProviderName(), options.getProjectId());
    }

    @Override
    public void shutdown() {
        if (client != null) {
            try {
                closeClient();
            } catch (Exception e) {
                log.warn("Error closing client for {}", getProviderName(), e);
            }
            client = null;
        }
        log.info("{} shut down", getProviderName());
    }

    @Override
    public final ProviderEvaluation<Boolean> getBooleanEvaluation(
            String key, Boolean defaultValue, EvaluationContext ctx) {
        return evaluate(key, Boolean.class);
    }

    @Override
    public final ProviderEvaluation<String> getStringEvaluation(
            String key, String defaultValue, EvaluationContext ctx) {
        return evaluate(key, String.class);
    }

    @Override
    public final ProviderEvaluation<Integer> getIntegerEvaluation(
            String key, Integer defaultValue, EvaluationContext ctx) {
        return evaluate(key, Integer.class);
    }

    @Override
    public final ProviderEvaluation<Double> getDoubleEvaluation(
            String key, Double defaultValue, EvaluationContext ctx) {
        return evaluate(key, Double.class);
    }

    @Override
    public final ProviderEvaluation<Value> getObjectEvaluation(String key, Value defaultValue, EvaluationContext ctx) {
        return evaluate(key, Value.class);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    protected <T> ProviderEvaluation<T> evaluate(String key, Class<T> targetType) {
        String rawValue = fetchWithCache(key);
        T value = FlagValueConverter.convert(rawValue, targetType);
        return ProviderEvaluation.<T>builder()
                .value(value)
                .reason(Reason.STATIC.toString())
                .build();
    }

    protected String fetchWithCache(String key) {
        String name = buildName(key);
        Optional<String> cached = cache.get(name);
        if (cached.isPresent()) {
            log.debug("Fetching from cache name '{}'", key);
            return cached.get();
        }
        synchronized (cache) {
            return cache.get(name).orElseGet(() -> {
                String value = fetchFromGcp(name);
                cache.put(name, value);
                return value;
            });
        }
    }

    protected String buildName(String flagKey) {
        String prefix = options.getNamePrefix();
        return (prefix != null && !prefix.isEmpty()) ? prefix + flagKey : flagKey;
    }

    // Subclasses must implement these
    protected abstract String getProviderName();

    protected abstract void createClient() throws Exception;

    protected abstract void closeClient() throws Exception;

    protected abstract String fetchFromGcp(String name);
}
