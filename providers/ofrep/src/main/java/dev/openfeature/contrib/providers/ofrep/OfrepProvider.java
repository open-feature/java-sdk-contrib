package dev.openfeature.contrib.providers.ofrep;

import dev.openfeature.contrib.providers.ofrep.internal.Resolver;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.Metadata;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Value;
import org.apache.commons.validator.routines.UrlValidator;

/**
 * OpenFeature provider for OFREP.
 */
public final class OfrepProvider implements FeatureProvider {

    private static final String OFREP_PROVIDER = "ofrep";
    private final Resolver ofrepResolver;

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

        if (options.getTimeout() == null
                || options.getTimeout().isNegative()
                || options.getTimeout().isZero()) {
            throw new IllegalArgumentException("Timeout must be a positive duration");
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
        this.ofrepResolver = new Resolver(
                options.getBaseUrl(),
                options.getHeaders(),
                options.getTimeout(),
                options.getProxySelector(),
                options.getExecutor());
    }

    @Override
    public Metadata getMetadata() {
        return () -> OFREP_PROVIDER;
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
