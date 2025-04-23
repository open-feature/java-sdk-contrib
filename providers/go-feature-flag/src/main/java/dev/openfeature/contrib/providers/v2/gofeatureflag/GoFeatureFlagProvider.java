package dev.openfeature.contrib.providers.v2.gofeatureflag;

import dev.openfeature.contrib.providers.v2.gofeatureflag.api.GoFeatureFlagApi;
import dev.openfeature.contrib.providers.v2.gofeatureflag.bean.EvaluationType;
import dev.openfeature.contrib.providers.v2.gofeatureflag.controller.EdgeEvaluator;
import dev.openfeature.contrib.providers.v2.gofeatureflag.controller.IEvaluator;
import dev.openfeature.contrib.providers.v2.gofeatureflag.controller.InProcessEvaluator;
import dev.openfeature.contrib.providers.v2.gofeatureflag.exception.InvalidOptions;
import dev.openfeature.contrib.providers.v2.gofeatureflag.service.EvaluationService;
import dev.openfeature.contrib.providers.v2.gofeatureflag.validator.Validator;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.EventProvider;
import dev.openfeature.sdk.Hook;
import dev.openfeature.sdk.Metadata;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.ProviderEventDetails;
import dev.openfeature.sdk.Tracking;
import dev.openfeature.sdk.TrackingEventDetails;
import dev.openfeature.sdk.Value;
import java.util.List;
import java.util.function.Consumer;

public class GoFeatureFlagProvider extends EventProvider implements Tracking {
    /**
     * Options to configure the provider.
     */
    private final GoFeatureFlagProviderOptions options;

    /**
     * Service to evaluate the flags.
     */
    private final EvaluationService evalService;

    /**
     * Constructor of the provider.
     *
     * @param options - options to configure the provider
     * @throws InvalidOptions - if options are invalid
     */
    public GoFeatureFlagProvider(GoFeatureFlagProviderOptions options) throws InvalidOptions {
        Validator.ProviderOptions(options);
        this.options = options;
        this.evalService = new EvaluationService(
                getEvaluator(GoFeatureFlagApi.builder().options(options).build()));
    }


    @Override
    public Metadata getMetadata() {
        return () -> "GO Feature Flag Provider";
    }

    @Override
    public List<Hook> getProviderHooks() {
        return super.getProviderHooks();
    }

    @Override
    public ProviderEvaluation<Boolean> getBooleanEvaluation(
            String key, Boolean defaultValue, EvaluationContext evaluationContext) {
        return this.evalService.getEvaluation(key, defaultValue, evaluationContext, Boolean.class);
    }

    @Override
    public ProviderEvaluation<String> getStringEvaluation(
            String key, String defaultValue, EvaluationContext evaluationContext) {
        return this.evalService.getEvaluation(key, defaultValue, evaluationContext, String.class);
    }

    @Override
    public ProviderEvaluation<Integer> getIntegerEvaluation(
            String key, Integer defaultValue, EvaluationContext evaluationContext) {
        return this.evalService.getEvaluation(key, defaultValue, evaluationContext, Integer.class);
    }

    @Override
    public ProviderEvaluation<Double> getDoubleEvaluation(
            String key, Double defaultValue, EvaluationContext evaluationContext) {
        return this.evalService.getEvaluation(key, defaultValue, evaluationContext, Double.class);
    }

    @Override
    public ProviderEvaluation<Value> getObjectEvaluation(
            String key, Value defaultValue, EvaluationContext evaluationContext) {
        return this.evalService.getEvaluation(key, defaultValue, evaluationContext, Value.class);
    }

    @Override
    public void initialize(EvaluationContext evaluationContext) throws Exception {
        super.initialize(evaluationContext);
        this.evalService.init();
    }

    @Override
    public void shutdown() {
        super.shutdown();
        this.evalService.destroy();
    }

    @Override
    public void track(String s) {

    }

    @Override
    public void track(String s, EvaluationContext evaluationContext) {

    }

    @Override
    public void track(String s, TrackingEventDetails trackingEventDetails) {

    }

    @Override
    public void track(String eventName, EvaluationContext context, TrackingEventDetails details) {
        super.track(eventName, context, details);
    }


    /**
     * Get the evaluator based on the evaluation type.
     * It will initialize the evaluator based on the evaluation type.
     *
     * @return the evaluator
     */
    private IEvaluator getEvaluator(GoFeatureFlagApi api) {
        // Select the evaluator based on the evaluation type
        if (options.getEvaluationType() == null || options.getEvaluationType() == EvaluationType.IN_PROCESS) {
            Consumer<ProviderEventDetails> emitProviderConfigurationChanged = this::emitProviderConfigurationChanged;
            return new InProcessEvaluator(api, this.options, emitProviderConfigurationChanged);
        }
        return new EdgeEvaluator(api);
    }
}
