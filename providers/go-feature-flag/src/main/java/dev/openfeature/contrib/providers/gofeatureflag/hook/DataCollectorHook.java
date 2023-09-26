package dev.openfeature.contrib.providers.gofeatureflag.hook;

import com.fasterxml.jackson.core.JsonProcessingException;
import dev.openfeature.contrib.providers.gofeatureflag.GoFeatureFlagProvider;
import dev.openfeature.contrib.providers.gofeatureflag.exception.InvalidOptions;
import dev.openfeature.contrib.providers.gofeatureflag.hook.events.Event;
import dev.openfeature.contrib.providers.gofeatureflag.hook.events.Events;
import dev.openfeature.contrib.providers.gofeatureflag.hook.events.EventsPublisher;
import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.Hook;
import dev.openfeature.sdk.HookContext;
import dev.openfeature.sdk.Reason;
import dev.openfeature.sdk.exceptions.GeneralError;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;

/**
 * DataCollectorHook is an OpenFeature Hook in charge of sending the usage of the flag to GO Feature Flag.
 */
@Slf4j
public class DataCollectorHook implements Hook {
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
                .contextKind(ctx.getCtx().getValue("anonymous").asBoolean() ? "anonymousUser" : "user")
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
                .contextKind(ctx.getCtx().getValue("anonymous").asBoolean() ? "anonymousUser" : "user")
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
        try {
            Events events = new Events(eventsList);
            HttpUrl url = this.options.getParsedEndpoint().newBuilder()
                    .addEncodedPathSegment("v1")
                    .addEncodedPathSegment("data")
                    .addEncodedPathSegment("collector")
                    .build();

            Request.Builder reqBuilder = new Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(
                            GoFeatureFlagProvider.requestMapper.writeValueAsBytes(events),
                            MediaType.get("application/json; charset=utf-8")));

            if (this.options.getApiKey() != null && !this.options.getApiKey().isEmpty()) {
                reqBuilder.addHeader("Authorization", "Bearer " + this.options.getApiKey());
            }

            try (Response response = this.options.getHttpClient().newCall(reqBuilder.build()).execute()) {
                if (response.code() == HTTP_UNAUTHORIZED) {
                    throw new GeneralError("Unauthorized");
                }
                if (response.code() >= HTTP_BAD_REQUEST) {
                    throw new GeneralError("Bad request: " + response.body());
                }

                if (response.code() == HTTP_OK) {
                    log.info("Published {} events successfully: {}", eventsList.size(), response.body());
                }
            } catch (IOException e) {
                throw new GeneralError("Impossible to send the usage data to GO Feature Flag", e);
            }
        } catch (JsonProcessingException e) {
            throw new GeneralError("Impossible to convert data collector events", e);
        }
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
}
