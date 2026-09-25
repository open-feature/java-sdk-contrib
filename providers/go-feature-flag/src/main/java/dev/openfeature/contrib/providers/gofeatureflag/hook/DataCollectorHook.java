package dev.openfeature.contrib.providers.gofeatureflag.hook;

import dev.openfeature.contrib.providers.gofeatureflag.bean.FeatureEvent;
import dev.openfeature.contrib.providers.gofeatureflag.bean.IEvent;
import dev.openfeature.contrib.providers.gofeatureflag.evaluator.IEvaluator;
import dev.openfeature.contrib.providers.gofeatureflag.exception.InvalidOptions;
import dev.openfeature.contrib.providers.gofeatureflag.service.EventsPublisher;
import dev.openfeature.contrib.providers.gofeatureflag.util.Const;
import dev.openfeature.contrib.providers.gofeatureflag.util.EvaluationContextUtil;
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
    /** evaluator is the service to evaluate the flags. */
    private final IEvaluator evaluator;

    /**
     * Constructor of the hook.
     *
     * @param options - Options to configure the hook
     * @throws InvalidOptions - Thrown when there is a missing configuration.
     */
    public DataCollectorHook(final DataCollectorHookOptions options) throws InvalidOptions {
        if (options == null) {
            throw new InvalidOptions("DataCollectorHookOptions cannot be null");
        }
        options.validate();
        eventsPublisher = options.getEventsPublisher();
        evaluator = options.getEvaluator();
        this.options = options;
    }

    @Override
    public void after(HookContext ctx, FlagEvaluationDetails details, Map hints) {
        // the relay proxy evaluated this flag itself and recorded it server side as it did so
        if (wasEvaluatedRemotely(details)
                || !this.evaluator.isFlagTrackable(ctx.getFlagKey())
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

    /**
     * wasEvaluatedRemotely reports whether this result came from the relay proxy rather than the
     * local engine.
     *
     * <p>Only the after stage consults it. The error stage is reached when the fallback failed as
     * well, and a relay proxy that could not answer recorded nothing to duplicate.</p>
     *
     * @param details - result the SDK is about to hand to the caller
     * @return true if the relay proxy produced this result
     */
    private static boolean wasEvaluatedRemotely(final FlagEvaluationDetails<?> details) {
        return details.getFlagMetadata() != null
                && Boolean.TRUE.equals(details.getFlagMetadata().getBoolean(Const.METADATA_EVALUATED_REMOTELY));
    }

    /** shutdown should be called when we stop the hook, it will publish the remaining event. */
    public void shutdown() {
        // eventsPublisher is required so no need to check if it is null
        eventsPublisher.shutdown();
    }
}
