package dev.openfeature.contrib.tools.flagd.resolver.process.storage.connector.sync.http;

import static java.net.http.HttpClient.Builder.NO_PROXY;

import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.QueuePayload;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.QueuePayloadType;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.QueueSource;
import dev.openfeature.contrib.tools.flagd.resolver.process.storage.connector.sync.http.util.ConcurrentUtils;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * HttpConnector is responsible for polling data from a specified URL at regular intervals.
 * Notice rate limits for polling http sources like Github.
 * It implements the QueueSource interface to enqueue and dequeue change messages.
 * The class supports configurable parameters such as poll interval, request timeout, and proxy settings.
 * It uses a ScheduledExecutorService to schedule polling tasks and an ExecutorService for HTTP client execution.
 * The class also provides methods to initialize, retrieve the stream queue, and shutdown the connector gracefully.
 * It supports optional fail-safe initialization via cache.
 * See readme - Http Connector section.
 */
@Slf4j
public class HttpConnector implements QueueSource {

    public static final String POLLING_PAYLOAD_CACHE_KEY = HttpConnector.class.getSimpleName() + ".polling-payload";
    private Integer pollIntervalSeconds;
    private Integer requestTimeoutSeconds;
    private BlockingQueue<QueuePayload> queue;
    private HttpClient client;
    private ExecutorService httpClientExecutor;
    private ScheduledExecutorService scheduler;
    private Map<String, String> headers;
    private FailSafeCache failSafeCache;
    private PayloadCache payloadCache;
    private HttpCacheFetcher httpCacheFetcher;
    private int payloadCachePollTtlSeconds;
    private boolean usePollingCache;

    @NonNull
    private String url;

    /**
     * HttpConnector constructor.
     *
     * @param httpConnectorOptions options for configuring the HttpConnector.
     */
    @Builder
    public HttpConnector(HttpConnectorOptions httpConnectorOptions) {
        this.pollIntervalSeconds = httpConnectorOptions.getPollIntervalSeconds();
        this.requestTimeoutSeconds = httpConnectorOptions.getRequestTimeoutSeconds();
        ProxySelector proxySelector = NO_PROXY;
        if (httpConnectorOptions.getProxyHost() != null && httpConnectorOptions.getProxyPort() != null) {
            proxySelector = ProxySelector.of(new InetSocketAddress(httpConnectorOptions.getProxyHost(),
                httpConnectorOptions.getProxyPort()));
        }
        this.url = httpConnectorOptions.getUrl();
        this.headers = httpConnectorOptions.getHeaders();
        this.httpClientExecutor = httpConnectorOptions.getHttpClientExecutor();
        scheduler = Executors.newScheduledThreadPool(httpConnectorOptions.getScheduledThreadPoolSize());
        this.client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(httpConnectorOptions.getConnectTimeoutSeconds()))
            .proxy(proxySelector)
            .executor(this.httpClientExecutor)
            .build();
        this.queue = new LinkedBlockingQueue<>(httpConnectorOptions.getLinkedBlockingQueueCapacity());
        this.payloadCache = httpConnectorOptions.getPayloadCache();
        if (payloadCache != null && Boolean.TRUE.equals(httpConnectorOptions.getUseFailsafeCache())) {
            this.failSafeCache = FailSafeCache.builder()
                .payloadCache(payloadCache)
                .payloadCacheOptions(httpConnectorOptions.getPayloadCacheOptions())
                .build();
        }
        if (Boolean.TRUE.equals(httpConnectorOptions.getUseHttpCache())) {
            httpCacheFetcher = new HttpCacheFetcher();
        }
        payloadCachePollTtlSeconds = pollIntervalSeconds; // safety margin
        this.usePollingCache = Boolean.TRUE.equals(httpConnectorOptions.getUsePollingCache());
    }

    @Override
    public void init() throws Exception {
        log.info("init Http Connector");
    }

    @SuppressFBWarnings(
        value = "EI_EXPOSE_REP",
        justification = "parent defines the interface"
    )
    @Override
    public BlockingQueue<QueuePayload> getStreamQueue() {
        boolean success = fetchAndUpdate();
        if (!success) {
            log.info("failed initial fetch");
            updateFromCache();
        }
        Runnable pollTask = buildPollTask();
        scheduler.scheduleWithFixedDelay(pollTask, pollIntervalSeconds, pollIntervalSeconds, TimeUnit.SECONDS);
        return queue;
    }

    private void updateFromCache() {
        log.info("taking initial payload from cache to avoid starting with default values");
        String flagData = null;
        if (payloadCache != null) {
            flagData = payloadCache.get(POLLING_PAYLOAD_CACHE_KEY);
            if (flagData != null) {
                log.debug("got payload from polling cache key");
            }
        }
        if (flagData == null) {
            if (failSafeCache == null) {
                log.debug("no failsafe cache, skipping");
                return;
            }
            flagData = failSafeCache.get();
            if (flagData == null) {
                log.debug("could not get from failsafe cache");
                return;
            }
        }
        if (!this.queue.offer(new QueuePayload(QueuePayloadType.DATA, flagData))) {
            log.warn("init: Unable to offer file content to queue: queue is full");
        }
    }

    protected Runnable buildPollTask() {
        return this::fetchAndUpdate;
    }

    private boolean fetchAndUpdate() {
        if (payloadCache != null && usePollingCache) {
            log.debug("checking cache for polling payload");
            String payload = payloadCache.get(POLLING_PAYLOAD_CACHE_KEY);
            if (payload != null) {
                log.debug("got payload from polling cache key, skipping update");
                return true;
            }
        }
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(requestTimeoutSeconds))
            .GET();
        headers.forEach(requestBuilder::header);

        HttpResponse<String> response;
        try {
            log.debug("fetching response");
            response = execute(requestBuilder);
        } catch (IOException e) {
            log.info("could not fetch", e);
            return false;
        } catch (Exception e) {
            log.debug("exception", e);
            return false;
        }
        log.debug("fetched response");
        String payload = response.body();
        if (!isSuccessful(response)) {
            log.info("received non-successful status code: {} {}", response.statusCode(), payload);
            return false;
        } else if (response.statusCode() == 304) {
            log.debug("got 304 Not Modified, skipping update");
            return true;
        }
        if (payload == null) {
            log.debug("payload is null");
            return false;
        }
        log.debug("adding payload to queue");
        if (!this.queue.offer(new QueuePayload(QueuePayloadType.DATA, payload))) {
            log.warn("Unable to offer file content to queue: queue is full");
            return false;
        }
        if (payloadCache != null) {
            log.debug("scheduling cache update if needed");
            scheduler.execute(() -> {
                    if (failSafeCache != null) {
                        log.debug("updating payload in failsafe cache if needed");
                        failSafeCache.updatePayloadIfNeeded(payload);
                    }
                    if (payloadCache != null) {
                        log.debug("updating polling payload in cache");
                        payloadCache.put(POLLING_PAYLOAD_CACHE_KEY, payload, payloadCachePollTtlSeconds);
                    }
                }
            );
        }
        return true;
    }

    private static boolean isSuccessful(HttpResponse<String> response) {
        return response.statusCode() == 200 || response.statusCode() == 304;
    }

    protected HttpResponse<String> execute(HttpRequest.Builder requestBuilder)
            throws IOException, InterruptedException {
        if (httpCacheFetcher != null) {
            return httpCacheFetcher.fetchContent(client, requestBuilder);
        }
        return client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Override
    public void shutdown() throws InterruptedException {
        ConcurrentUtils.shutdownAndAwaitTermination(scheduler, 10);
        ConcurrentUtils.shutdownAndAwaitTermination(httpClientExecutor, 10);
    }
}
