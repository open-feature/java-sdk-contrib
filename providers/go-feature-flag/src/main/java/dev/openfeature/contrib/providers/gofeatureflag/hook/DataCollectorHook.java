package dev.openfeature.contrib.providers.gofeatureflag.hook;

import dev.openfeature.contrib.providers.gofeatureflag.bean.FeatureEvent;
import dev.openfeature.contrib.providers.gofeatureflag.bean.IEvent;
import dev.openfeature.contrib.providers.gofeatureflag.exception.InvalidOptions;
import dev.openfeature.contrib.providers.gofeatureflag.service.EvaluationService;
import dev.openfeature.contrib.providers.gofeatureflag.service.EventsPublisher;
import dev.openfeature.contrib.providers.gofeatureflag.util.EvaluationContextUtil;
import dev.openfeature.contrib.providers.gofeatureflag.validator.Validator;
import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.Hook;
import dev.openfeature.sdk.HookContext;
import dev.openfeature.sdk.Reason;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * DataCollectorHook is an OpenFeature Hook in charge of sending the usage of the flag to GO Feature
 * Flag.
 */
@Slf4j
public final class DataCollectorHook implements Hook<HookContext<String>> {
    /** options contains all the options of this hook. */
    private final DataCollectorHookOptions options;
    /** eventsPublisher is the system collecting all the information to send to GO Feature Flag. */
    private final EventsPublisher<IEvent> eventsPublisher;
    /** evalService is the service to evaluate the flags. */
    private final EvaluationService evalService;

    /**
     * Constructor of the hook.
     *
     * @param options - Options to configure the hook
     * @throws InvalidOptions - Thrown when there is a missing configuration.
     */
    public DataCollectorHook(final DataCollectorHookOptions options) throws InvalidOptions {
        Validator.dataCollectorHookOptions(options);
        eventsPublisher = options.getEventsPublisher();
        evalService = options.getEvalService();
        this.options = options;
    }

    @Override
    public void after(HookContext ctx, FlagEvaluationDetails details, Map hints) {
        if (!this.evalService.isFlagTrackable(ctx.getFlagKey())
                || (!Boolean.TRUE.equals(this.options.getCollectUnCachedEvaluation())
                        && !Reason.CACHED.name().equals(details.getReason()))) {
            return;
        }

        IEvent event = FeatureEvent.builder()
                .key(ctx.getFlagKey())
                .kind("feature")
                .contextKind(EvaluationContextUtil.isAnonymousUser(ctx.getCtx()) ? "anonymousUser" : "user")
                .defaultValue(false)
                .variation(details.getVariant())
                .value(details.getValue())
                .userKey(ctx.getCtx().getTargetingKey())
                .creationDate(System.currentTimeMillis() / 1000L)
                .build();
        eventsPublisher.add(event);
    }

    @Override
    public void error(HookContext ctx, Exception error, Map hints) {
        IEvent event = FeatureEvent.builder()
                .key(ctx.getFlagKey())
                .kind("feature")
                .contextKind(EvaluationContextUtil.isAnonymousUser(ctx.getCtx()) ? "anonymousUser" : "user")
                .creationDate(System.currentTimeMillis() / 1000L)
                .defaultValue(true)
                .variation("SdkDefault")
                .value(ctx.getDefaultValue())
                .userKey(ctx.getCtx().getTargetingKey())
                .build();
        eventsPublisher.add(event);
    }

    /** shutdown should be called when we stop the hook, it will publish the remaining event. */
    public void shutdown() {
        // eventsPublisher is required so no need to check if it is null
        eventsPublisher.shutdown();
    }
}
