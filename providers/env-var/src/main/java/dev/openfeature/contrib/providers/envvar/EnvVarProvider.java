package dev.openfeature.contrib.providers.envvar;

import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.Metadata;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Reason;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.FlagNotFoundError;
import dev.openfeature.sdk.exceptions.ParseError;
import dev.openfeature.sdk.exceptions.ValueNotConvertableError;
import java.util.function.Function;

/** EnvVarProvider is the Java provider implementation for the environment variables. */
public final class EnvVarProvider implements FeatureProvider {
    private static final String NAME = "Environment Variables Provider";

    private final EnvironmentGateway environmentGateway;
    private final EnvironmentKeyTransformer keyTransformer;

    public EnvVarProvider() {
        this(new OS(), EnvironmentKeyTransformer.doNothing());
    }

    public EnvVarProvider(EnvironmentGateway environmentGateway) {
        this(environmentGateway, EnvironmentKeyTransformer.doNothing());
    }

    public EnvVarProvider(EnvironmentKeyTransformer keyTransformer) {
        this(new OS(), keyTransformer);
    }

    public EnvVarProvider(EnvironmentGateway environmentGateway, EnvironmentKeyTransformer keyTransformer) {
        this.environmentGateway = environmentGateway;
        this.keyTransformer = keyTransformer;
    }

    @Override
    public Metadata getMetadata() {
        return () -> NAME;
    }

    @Override
    public ProviderEvaluation<Boolean> getBooleanEvaluation(String key, Boolean defaultValue, EvaluationContext ctx) {
        return evaluateEnvironmentVariable(key, Boolean::valueOf);
    }

    @Override
    public ProviderEvaluation<String> getStringEvaluation(String key, String defaultValue, EvaluationContext ctx) {
        return evaluateEnvironmentVariable(key, string -> string);
    }

    @Override
    public ProviderEvaluation<Integer> getIntegerEvaluation(String key, Integer defaultValue, EvaluationContext ctx) {
        return evaluateEnvironmentVariable(key, Integer::valueOf);
    }

    @Override
    public ProviderEvaluation<Double> getDoubleEvaluation(String key, Double defaultValue, EvaluationContext ctx) {
        return evaluateEnvironmentVariable(key, Double::valueOf);
    }

    @Override
    public ProviderEvaluation<Value> getObjectEvaluation(String key, Value defaultValue, EvaluationContext ctx) {
        throw new ValueNotConvertableError("EnvVarProvider supports only primitives");
    }

    private <T> ProviderEvaluation<T> evaluateEnvironmentVariable(String key, Function<String, T> parse) {
        final String value = environmentGateway.getEnvironmentVariable(keyTransformer.transformKey(key));

        if (value == null) {
            throw new FlagNotFoundError();
        }

        try {
            return ProviderEvaluation.<T>builder()
                    .value(parse.apply(value))
                    .reason(Reason.STATIC.toString())
                    .build();
        } catch (Exception e) {
            throw new ParseError(e.getMessage() != null ? e.getMessage() : "Unknown parsing error", e);
        }
    }
}
