package dev.openfeature.contrib.providers.ofrep;

import dev.openfeature.contrib.providers.ofrep.internal.Resolver;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.Metadata;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Value;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.validator.routines.UrlValidator;

/**
 * OpenFeature provider for OFREP.
 */
@Slf4j
public final class OfrepProvider implements FeatureProvider {

    private static final String OFREP_PROVIDER = "ofrep";
    private static final long DEFAULT_EXECUTOR_SHUTDOWN_TIMEOUT = 1000;

    private final Resolver ofrepResolver;
    private Executor executor;

    public static OfrepProvider constructProvider() {
        return new OfrepProvider();
    }

    /**
     * Constructs an OfrepProvider with the specified options.
     *
     * @param options The options for configuring the provider.
     * @return An instance of OfrepProvider configured with the provided options.
     * @throws IllegalArgumentException if any of the options are invalid.
     */
    public static OfrepProvider constructProvider(OfrepProviderOptions options) {
        if (!isValidUrl(options.getBaseUrl())) {
            throw new IllegalArgumentException("Invalid base URL: " + options.getBaseUrl());
        }

        if (options.getHeaders() == null) {
            throw new IllegalArgumentException("Headers cannot be null");
        }

        if (options.getRequestTimeout() == null
                || options.getRequestTimeout().isNegative()
                || options.getRequestTimeout().isZero()) {
            throw new IllegalArgumentException("Request timeout must be a positive duration");
        }

        if (options.getConnectTimeout() == null
                || options.getConnectTimeout().isNegative()
                || options.getConnectTimeout().isZero()) {
            throw new IllegalArgumentException("Connect timeout must be a positive duration");
        }

        if (options.getProxySelector() == null) {
            throw new IllegalArgumentException("ProxySelector cannot be null");
        }

        if (options.getExecutor() == null) {
            throw new IllegalArgumentException("Executor cannot be null");
        }

        return new OfrepProvider(options);
    }

    private OfrepProvider() {
        this(new OfrepProviderOptions.Builder().build());
    }

    private OfrepProvider(OfrepProviderOptions options) {
        this.executor = options.getExecutor();
        this.ofrepResolver = new Resolver(
                options.getBaseUrl(),
                options.getHeaders(),
                options.getRequestTimeout(),
                options.getConnectTimeout(),
                options.getProxySelector(),
                options.getExecutor());
    }

    @Override
    public Metadata getMetadata() {
        return () -> OFREP_PROVIDER;
    }

    @Override
    public void shutdown() {
        if (executor instanceof ExecutorService) {
            try {
                ExecutorService executorService = (ExecutorService) executor;
                executorService.shutdownNow();
                executorService.awaitTermination(DEFAULT_EXECUTOR_SHUTDOWN_TIMEOUT, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                log.error("Error during shutdown {}", OFREP_PROVIDER, e);
            }
        }
    }

    @Override
    public ProviderEvaluation<Boolean> getBooleanEvaluation(String key, Boolean defaultValue, EvaluationContext ctx) {
        return ofrepResolver.resolveBoolean(key, defaultValue, ctx);
    }

    @Override
    public ProviderEvaluation<String> getStringEvaluation(String key, String defaultValue, EvaluationContext ctx) {
        return ofrepResolver.resolveString(key, defaultValue, ctx);
    }

    @Override
    public ProviderEvaluation<Integer> getIntegerEvaluation(String key, Integer defaultValue, EvaluationContext ctx) {
        return ofrepResolver.resolveInteger(key, defaultValue, ctx);
    }

    @Override
    public ProviderEvaluation<Double> getDoubleEvaluation(String key, Double defaultValue, EvaluationContext ctx) {
        return ofrepResolver.resolveDouble(key, defaultValue, ctx);
    }

    @Override
    public ProviderEvaluation<Value> getObjectEvaluation(String key, Value defaultValue, EvaluationContext ctx) {
        return ofrepResolver.resolveObject(key, defaultValue, ctx);
    }

    private static boolean isValidUrl(String url) {
        UrlValidator validator = new UrlValidator(new String[] {"http", "https"}, UrlValidator.ALLOW_LOCAL_URLS);
        return validator.isValid(url);
    }
}
