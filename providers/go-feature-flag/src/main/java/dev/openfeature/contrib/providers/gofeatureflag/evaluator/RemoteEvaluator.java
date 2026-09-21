package dev.openfeature.contrib.providers.gofeatureflag.evaluator;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import dev.openfeature.contrib.providers.gofeatureflag.GoFeatureFlagProviderOptions;
import dev.openfeature.contrib.providers.gofeatureflag.util.Const;
import dev.openfeature.contrib.providers.ofrep.OfrepProvider;
import dev.openfeature.contrib.providers.ofrep.OfrepProviderOptions;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.ProviderEvent;
import dev.openfeature.sdk.ProviderEventDetails;
import dev.openfeature.sdk.Value;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * RemoteEvaluator is an implementation of the IEvaluator interface.
 * It is used to evaluate the feature flags using the GO Feature Flag API.
 */
@Slf4j
public class RemoteEvaluator implements IEvaluator {
    /**
     * Start of the error message the OFREP client reports for a 401 or 403. That client folds every
     * status into a GENERAL error code, so the message is the only part of the result that still
     * distinguishes rejected credentials from an ordinary failure.
     */
    private static final String OFREP_AUTHENTICATION_ERROR = "authentication/authorization error for flag:";

    /** OFREP provider doing the actual remote evaluation. */
    private final OfrepProvider ofrep;
    /** Method to call to emit a provider event to the SDK. */
    private final BiConsumer<ProviderEvent, ProviderEventDetails> emitter;
    /** Guards against re-reporting the same authentication failure on every later evaluation. */
    private final AtomicBoolean authenticationFailureReported = new AtomicBoolean();

    /**
     * Constructor of the evaluator.
     *
     * @param opts    - options to configure the provider
     * @param emitter - method to call to emit a provider event to the SDK
     */
    public RemoteEvaluator(GoFeatureFlagProviderOptions opts, BiConsumer<ProviderEvent, ProviderEventDetails> emitter) {
        this.emitter = emitter;
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
        this.authenticationFailureReported.set(false);
        this.ofrep.initialize(ctx, domain);
    }

    @Override
    public void initialize(final EvaluationContext ctx) throws Exception {
        this.authenticationFailureReported.set(false);
        this.ofrep.initialize(ctx);
    }

    @Override
    public void shutdown() {
        this.ofrep.shutdown();
    }

    @Override
    public ProviderEvaluation<Boolean> getBooleanEvaluation(String key, Boolean defaultValue, EvaluationContext ctx) {
        return reportAuthenticationFailure(this.ofrep.getBooleanEvaluation(key, defaultValue, ctx));
    }

    @Override
    public ProviderEvaluation<String> getStringEvaluation(String key, String defaultValue, EvaluationContext ctx) {
        return reportAuthenticationFailure(this.ofrep.getStringEvaluation(key, defaultValue, ctx));
    }

    @Override
    public ProviderEvaluation<Integer> getIntegerEvaluation(String key, Integer defaultValue, EvaluationContext ctx) {
        return reportAuthenticationFailure(this.ofrep.getIntegerEvaluation(key, defaultValue, ctx));
    }

    @Override
    public ProviderEvaluation<Double> getDoubleEvaluation(String key, Double defaultValue, EvaluationContext ctx) {
        return reportAuthenticationFailure(this.ofrep.getDoubleEvaluation(key, defaultValue, ctx));
    }

    @Override
    public ProviderEvaluation<Value> getObjectEvaluation(String key, Value defaultValue, EvaluationContext ctx) {
        return reportAuthenticationFailure(this.ofrep.getObjectEvaluation(key, defaultValue, ctx));
    }

    /**
     * reportAuthenticationFailure moves the provider to the fatal state when the relay proxy has
     * rejected our credentials.
     *
     * <p>Remote evaluation holds no configuration, so initialization has nothing to fetch and cannot
     * discover that the API key is wrong. The first evaluation is therefore the earliest point at
     * which the provider can learn it, and rejected credentials cannot be repaired by retrying, so
     * the SDK must be told rather than left reporting a per-call error forever.</p>
     *
     * @param evaluation - result returned by the OFREP client
     * @param <T>        - type of the flag value
     * @return the evaluation, unchanged
     */
    private <T> ProviderEvaluation<T> reportAuthenticationFailure(final ProviderEvaluation<T> evaluation) {
        if (evaluation.getErrorCode() != ErrorCode.GENERAL
                || evaluation.getErrorMessage() == null
                || !evaluation.getErrorMessage().startsWith(OFREP_AUTHENTICATION_ERROR)) {
            return evaluation;
        }

        if (this.authenticationFailureReported.compareAndSet(false, true)) {
            log.error("the relay proxy rejected our credentials, the provider cannot recover by retrying");
            this.emitter.accept(
                    ProviderEvent.PROVIDER_ERROR,
                    ProviderEventDetails.builder()
                            .errorCode(ErrorCode.PROVIDER_FATAL)
                            .message("authentication/authorization error while evaluating a flag remotely")
                            .build());
        }
        return evaluation;
    }
}
