package dev.openfeature.contrib.providers.gofeatureflag.evaluator;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import dev.openfeature.contrib.providers.gofeatureflag.GoFeatureFlagProviderOptions;
import dev.openfeature.contrib.providers.gofeatureflag.util.Const;
import dev.openfeature.contrib.providers.ofrep.OfrepProvider;
import dev.openfeature.contrib.providers.ofrep.OfrepProviderOptions;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Value;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * RemoteEvaluator is an implementation of the IEvaluator interface.
 * It is used to evaluate the feature flags using the GO Feature Flag API.
 */
@Slf4j
public class RemoteEvaluator implements IEvaluator {
    /** OFREP provider doing the actual remote evaluation. */
    private final OfrepProvider ofrep;

    /**
     * Constructor of the evaluator.
     *
     * @param opts - options to configure the provider
     */
    public RemoteEvaluator(GoFeatureFlagProviderOptions opts) {
        val headers = ImmutableMap.<String, ImmutableList<String>>builder();
        if (opts.getApiKey() != null && !opts.getApiKey().isEmpty()) {
            headers.put(Const.HTTP_HEADER_API_KEY, ImmutableList.of(opts.getApiKey()));
        }

        this.ofrep = OfrepProvider.constructProvider(OfrepProviderOptions.builder()
                .baseUrl(opts.getEndpoint().replaceAll("/+$", ""))
                .connectTimeout(Duration.ofMillis(opts.getTimeout()))
                .requestTimeout(Duration.ofMillis(opts.getTimeout()))
                .headers(headers.build())
                .build());
    }

    @Override
    public boolean isFlagTrackable(String flagKey) {
        return true;
    }

    @Override
    public void initialize(final EvaluationContext ctx, final String domain) throws Exception {
        this.ofrep.initialize(ctx, domain);
    }

    @Override
    public void initialize(final EvaluationContext ctx) throws Exception {
        this.ofrep.initialize(ctx);
    }

    @Override
    public void shutdown() {
        this.ofrep.shutdown();
    }

    @Override
    public ProviderEvaluation<Boolean> getBooleanEvaluation(String key, Boolean defaultValue, EvaluationContext ctx) {
        return this.ofrep.getBooleanEvaluation(key, defaultValue, ctx);
    }

    @Override
    public ProviderEvaluation<String> getStringEvaluation(String key, String defaultValue, EvaluationContext ctx) {
        return this.ofrep.getStringEvaluation(key, defaultValue, ctx);
    }

    @Override
    public ProviderEvaluation<Integer> getIntegerEvaluation(String key, Integer defaultValue, EvaluationContext ctx) {
        return this.ofrep.getIntegerEvaluation(key, defaultValue, ctx);
    }

    @Override
    public ProviderEvaluation<Double> getDoubleEvaluation(String key, Double defaultValue, EvaluationContext ctx) {
        return this.ofrep.getDoubleEvaluation(key, defaultValue, ctx);
    }

    @Override
    public ProviderEvaluation<Value> getObjectEvaluation(String key, Value defaultValue, EvaluationContext ctx) {
        return this.ofrep.getObjectEvaluation(key, defaultValue, ctx);
    }
}
