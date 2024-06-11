package dev.openfeature.contrib.providers.gofeatureflag.hook;

import dev.openfeature.contrib.providers.gofeatureflag.bean.GoFeatureFlagUser;
import dev.openfeature.contrib.providers.gofeatureflag.exception.InvalidOptions;
import dev.openfeature.contrib.providers.gofeatureflag.hook.events.Event;
import dev.openfeature.contrib.providers.gofeatureflag.hook.events.EventsPublisher;
import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.Hook;
import dev.openfeature.sdk.HookContext;
import dev.openfeature.sdk.Reason;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * DataCollectorHook is an OpenFeature Hook in charge of sending the usage of the flag to GO Feature Flag.
 */
@Slf4j
@SuppressWarnings({"checkstyle:NoFinalizer"})
public class DataCollectorHook implements Hook<HookContext<String>> {
    public static final long DEFAULT_FLUSH_INTERVAL_MS = Duration.ofMinutes(1).toMillis();
    public static final int DEFAULT_MAX_PENDING_EVENTS = 10000;
    /**
     * options contains all the options of this hook.
     */
    private final DataCollectorHookOptions options;
    /**
     * eventsPublisher is the system collecting all the information to send to GO Feature Flag.
     */
    private final EventsPublisher<Event> eventsPublisher;

    /**
     * Constructor of the hook.
     *
     * @param options - Options to configure the hook
     * @throws InvalidOptions - Thrown when there is a missing configuration.
     */
    public DataCollectorHook(DataCollectorHookOptions options) throws InvalidOptions {
        if (options == null) {
            throw new InvalidOptions("No options provided");
        }
        long flushIntervalMs = options.getFlushIntervalMs() == null
                ? DEFAULT_FLUSH_INTERVAL_MS : options.getFlushIntervalMs();
        int maxPendingEvents = options.getMaxPendingEvents() == null
                ? DEFAULT_MAX_PENDING_EVENTS : options.getMaxPendingEvents();
        Consumer<List<Event>> publisher = this::publishEvents;
        eventsPublisher = new EventsPublisher<>(publisher, flushIntervalMs, maxPendingEvents);
        this.options = options;
    }

    @Override
    public void after(HookContext ctx, FlagEvaluationDetails details, Map hints) {
        if ((this.options.getCollectUnCachedEvaluation() == null || !this.options.getCollectUnCachedEvaluation())
                && !Reason.CACHED.name().equals(details.getReason())) {
            return;
        }

        Event event = Event.builder()
                .key(ctx.getFlagKey())
                .kind("feature")
                .contextKind(GoFeatureFlagUser.isAnonymousUser(ctx.getCtx()) ? "anonymousUser" : "user")
                .defaultValue(false)
                .variation(details.getVariant())
                .value(details.getValue())
                .userKey(ctx.getCtx().getTargetingKey())
                .creationDate(System.currentTimeMillis())
                .build();
        eventsPublisher.add(event);
    }

    @Override
    public void error(HookContext ctx, Exception error, Map hints) {
        Event event = Event.builder()
                .key(ctx.getFlagKey())
                .kind("feature")
                .contextKind(GoFeatureFlagUser.isAnonymousUser(ctx.getCtx()) ? "anonymousUser" : "user")
                .creationDate(System.currentTimeMillis())
                .defaultValue(true)
                .variation("SdkDefault")
                .value(ctx.getDefaultValue())
                .userKey(ctx.getCtx().getTargetingKey())
                .build();
        eventsPublisher.add(event);
    }

    /**
     * publishEvents is calling the GO Feature Flag data/collector api to store the flag usage for analytics.
     *
     * @param eventsList - list of the event to send to GO Feature Flag
     */
    private void publishEvents(List<Event> eventsList) {
        this.options.getGofeatureflagController().sendEventToDataCollector(eventsList);
    }

    /**
     * shutdown should be called when we stop the hook, it will publish the remaining event.
     */
    public void shutdown() {
        try {
            if (eventsPublisher != null) {
                eventsPublisher.shutdown();
            }
        } catch (Exception e) {
            log.error("error publishing events on shutdown", e);
        }
    }

    protected final void finalize() {
        // DO NOT REMOVE, spotbugs: CT_CONSTRUCTOR_THROW
    }
}
