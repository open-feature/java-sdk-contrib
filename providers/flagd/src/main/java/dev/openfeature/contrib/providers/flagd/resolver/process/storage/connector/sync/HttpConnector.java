package dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync;

import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.QueuePayload;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.QueuePayloadType;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.QueueSource;
import dev.openfeature.contrib.providers.flagd.util.ConcurrentUtils;
import lombok.Builder;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.net.http.HttpClient.Builder.NO_PROXY;

/**
 * HttpConnector is responsible for polling data from a specified URL at regular intervals.
 * Notice rate limits for polling http sources like Github.
 * It implements the QueueSource interface to enqueue and dequeue change messages.
 * The class supports configurable parameters such as poll interval, request timeout, and proxy settings.
 * It uses a ScheduledExecutorService to schedule polling tasks and an ExecutorService for HTTP client execution.
 * The class also provides methods to initialize, retrieve the stream queue, and shutdown the connector gracefully.
 * It supports optional fail-safe initialization via cache.
 *
 * See readme - Http Connector section.
 */
@Slf4j
public class HttpConnector implements QueueSource {

    private static final int DEFAULT_POLL_INTERVAL_SECONDS = 60;
    private static final int DEFAULT_LINKED_BLOCKING_QUEUE_CAPACITY = 100;
    private static final int DEFAULT_SCHEDULED_THREAD_POOL_SIZE = 2;
    private static final int DEFAULT_REQUEST_TIMEOUT_SECONDS = 10;
    private static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 10;

    private Integer pollIntervalSeconds;
    private Integer requestTimeoutSeconds;
    private BlockingQueue<QueuePayload> queue;
    private HttpClient client;
    private ExecutorService httpClientExecutor;
    private ScheduledExecutorService scheduler;
    private Map<String, String> headers;
    private PayloadCacheWrapper payloadCacheWrapper;
    private PayloadCache payloadCache;
    private HttpCacheFetcher httpCacheFetcher;

    @NonNull
    private String url;

    @Builder
    public HttpConnector(Integer pollIntervalSeconds, Integer linkedBlockingQueueCapacity,
             Integer scheduledThreadPoolSize, Integer requestTimeoutSeconds, Integer connectTimeoutSeconds, String url,
             Map<String, String> headers, ExecutorService httpClientExecutor, String proxyHost, Integer proxyPort,
            PayloadCacheOptions payloadCacheOptions, PayloadCache payloadCache, Boolean useHttpCache) {
        validate(url, pollIntervalSeconds, linkedBlockingQueueCapacity, scheduledThreadPoolSize, requestTimeoutSeconds,
            connectTimeoutSeconds, proxyHost, proxyPort, payloadCacheOptions, payloadCache);
        this.pollIntervalSeconds = pollIntervalSeconds == null ? DEFAULT_POLL_INTERVAL_SECONDS : pollIntervalSeconds;
        int thisLinkedBlockingQueueCapacity = linkedBlockingQueueCapacity == null ? DEFAULT_LINKED_BLOCKING_QUEUE_CAPACITY : linkedBlockingQueueCapacity;
        int thisScheduledThreadPoolSize = scheduledThreadPoolSize == null ? DEFAULT_SCHEDULED_THREAD_POOL_SIZE : scheduledThreadPoolSize;
        this.requestTimeoutSeconds = requestTimeoutSeconds == null ? DEFAULT_REQUEST_TIMEOUT_SECONDS : requestTimeoutSeconds;
        int thisConnectTimeoutSeconds = connectTimeoutSeconds == null ? DEFAULT_CONNECT_TIMEOUT_SECONDS : connectTimeoutSeconds;
        ProxySelector proxySelector = NO_PROXY;
        if (proxyHost != null && proxyPort != null) {
            proxySelector = ProxySelector.of(new InetSocketAddress(proxyHost, proxyPort));
        }

        this.url = url;
        this.headers = headers;
        this.httpClientExecutor = httpClientExecutor == null ? Executors.newFixedThreadPool(1) :
            httpClientExecutor;
        scheduler = Executors.newScheduledThreadPool(thisScheduledThreadPoolSize);
        if (headers == null) {
            this.headers = new HashMap<>();
        }
        this.client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(thisConnectTimeoutSeconds))
            .proxy(proxySelector)
            .executor(this.httpClientExecutor)
            .build();
        this.queue = new LinkedBlockingQueue<>(thisLinkedBlockingQueueCapacity);
        this.payloadCache = payloadCache;
        if (payloadCache != null) {
            this.payloadCacheWrapper = PayloadCacheWrapper.builder()
                .payloadCache(payloadCache)
                .payloadCacheOptions(payloadCacheOptions)
                .build();
        }
        if (Boolean.TRUE.equals(useHttpCache)) {
            httpCacheFetcher = new HttpCacheFetcher();
        }
    }

    @SneakyThrows
    private void validate(String url, Integer pollIntervalSeconds, Integer linkedBlockingQueueCapacity,
            Integer scheduledThreadPoolSize, Integer requestTimeoutSeconds, Integer connectTimeoutSeconds,
            String proxyHost, Integer proxyPort, PayloadCacheOptions payloadCacheOptions,
            PayloadCache payloadCache) {
        new URL(url).toURI();
        if (pollIntervalSeconds != null && (pollIntervalSeconds < 1 || pollIntervalSeconds > 600)) {
            throw new IllegalArgumentException("pollIntervalSeconds must be between 1 and 600");
        }
        if (linkedBlockingQueueCapacity != null && (linkedBlockingQueueCapacity < 1 || linkedBlockingQueueCapacity > 1000)) {
            throw new IllegalArgumentException("linkedBlockingQueueCapacity must be between 1 and 1000");
        }
        if (scheduledThreadPoolSize != null && (scheduledThreadPoolSize < 1 || scheduledThreadPoolSize > 10)) {
            throw new IllegalArgumentException("scheduledThreadPoolSize must be between 1 and 10");
        }
        if (requestTimeoutSeconds != null && (requestTimeoutSeconds < 1 || requestTimeoutSeconds > 60)) {
            throw new IllegalArgumentException("requestTimeoutSeconds must be between 1 and 60");
        }
        if (connectTimeoutSeconds != null && (connectTimeoutSeconds < 1 || connectTimeoutSeconds > 60)) {
            throw new IllegalArgumentException("connectTimeoutSeconds must be between 1 and 60");
        }
        if (proxyPort != null && (proxyPort < 1 || proxyPort > 65535)) {
            throw new IllegalArgumentException("proxyPort must be between 1 and 65535");
        }
        if (proxyHost != null && proxyPort == null ) {
            throw new IllegalArgumentException("proxyPort must be set if proxyHost is set");
        } else if (proxyHost == null && proxyPort != null) {
            throw new IllegalArgumentException("proxyHost must be set if proxyPort is set");
        }
        if (payloadCacheOptions != null && payloadCache == null) {
            throw new IllegalArgumentException("payloadCache must be set if payloadCacheOptions is set");
        }
        if (payloadCache != null && payloadCacheOptions == null) {
            throw new IllegalArgumentException("payloadCacheOptions must be set if payloadCache is set");
        }
    }

    @Override
    public void init() throws Exception {
        log.info("init Http Connector");
    }

    @Override
    public BlockingQueue<QueuePayload> getStreamQueue() {
        boolean success = fetchAndUpdate();
        if (!success) {
            log.info("failed initial fetch");
            if (payloadCache != null) {
                updateFromCache();
            }
        }
        Runnable pollTask = buildPollTask();
        scheduler.scheduleAtFixedRate(pollTask, pollIntervalSeconds, pollIntervalSeconds, TimeUnit.SECONDS);
        return queue;
    }

    private void updateFromCache() {
        log.info("taking initial payload from cache to avoid starting with default values");
        String flagData = payloadCache.get();
        if (flagData == null) {
            log.debug("got null from cache");
            return;
        }
        if (!this.queue.offer(new QueuePayload(QueuePayloadType.DATA, flagData))) {
            log.warn("init: Unable to offer file content to queue: queue is full");
        }
    }

    protected Runnable buildPollTask() {
        return this::fetchAndUpdate;
    }

    private boolean fetchAndUpdate() {
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
            return false;
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
        if (payloadCacheWrapper != null) {
            log.debug("scheduling cache update if needed");
            scheduler.execute(() ->
                payloadCacheWrapper.updatePayloadIfNeeded(payload)
            );
        }
        return payload != null;
    }

    private static boolean isSuccessful(HttpResponse<String> response) {
        return response.statusCode() == 200 || response.statusCode() == 304;
    }

    protected HttpResponse<String> execute(HttpRequest.Builder requestBuilder) throws IOException, InterruptedException {
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
