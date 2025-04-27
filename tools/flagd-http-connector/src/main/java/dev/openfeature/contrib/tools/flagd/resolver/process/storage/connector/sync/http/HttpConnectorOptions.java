package dev.openfeature.contrib.tools.flagd.resolver.process.storage.connector.sync.http;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;

/**
 * Represents configuration options for the HTTP connector.
 */
@Getter
public class HttpConnectorOptions {

    @Builder.Default
    private Integer pollIntervalSeconds = 60;
    @Builder.Default
    private Integer connectTimeoutSeconds = 10;
    @Builder.Default
    private Integer requestTimeoutSeconds = 10;
    @Builder.Default
    private Integer linkedBlockingQueueCapacity = 100;
    @Builder.Default
    private Integer scheduledThreadPoolSize = 2;
    @Builder.Default
    private Map<String, String> headers = new HashMap<>();
    @Builder.Default
    private ExecutorService httpClientExecutor = Executors.newFixedThreadPool(1);
    @Builder.Default
    private String proxyHost;
    @Builder.Default
    private Integer proxyPort;
    @Builder.Default
    private PayloadCacheOptions payloadCacheOptions;
    @Builder.Default
    private PayloadCache payloadCache;
    @Builder.Default
    private Boolean useHttpCache;
    @NonNull
    private String url;

    /**
     * HttpConnectorOptions constructor.
     */
    @Builder
    public HttpConnectorOptions(Integer pollIntervalSeconds, Integer linkedBlockingQueueCapacity,
            Integer scheduledThreadPoolSize, Integer requestTimeoutSeconds, Integer connectTimeoutSeconds, String url,
            Map<String, String> headers, ExecutorService httpClientExecutor, String proxyHost, Integer proxyPort,
            PayloadCacheOptions payloadCacheOptions, PayloadCache payloadCache, Boolean useHttpCache) {
        validate(url, pollIntervalSeconds, linkedBlockingQueueCapacity, scheduledThreadPoolSize, requestTimeoutSeconds,
                connectTimeoutSeconds, proxyHost, proxyPort, payloadCacheOptions, payloadCache);
        if (pollIntervalSeconds != null) {
            this.pollIntervalSeconds = pollIntervalSeconds;
        }
        if (linkedBlockingQueueCapacity != null) {
            this.linkedBlockingQueueCapacity = linkedBlockingQueueCapacity;
        }
        if (scheduledThreadPoolSize != null) {
            this.scheduledThreadPoolSize = scheduledThreadPoolSize;
        }
        if (requestTimeoutSeconds != null) {
            this.requestTimeoutSeconds = requestTimeoutSeconds;
        }
        if (connectTimeoutSeconds != null) {
            this.connectTimeoutSeconds = connectTimeoutSeconds;
        }
        this.url = url;
        if (headers != null) {
            this.headers = headers;
        }
        if (httpClientExecutor != null) {
            this.httpClientExecutor = httpClientExecutor;
        }
        if (proxyHost != null) {
            this.proxyHost = proxyHost;
        }
        if (proxyPort != null) {
            this.proxyPort = proxyPort;
        }
        if (payloadCache != null) {
            this.payloadCache = payloadCache;
        }
        if (payloadCacheOptions != null) {
            this.payloadCacheOptions = payloadCacheOptions;
        }
        if (useHttpCache != null) {
            this.useHttpCache = useHttpCache;
        }
    }

    @SneakyThrows
    private void validate(String url, Integer pollIntervalSeconds, Integer linkedBlockingQueueCapacity,
            Integer scheduledThreadPoolSize, Integer requestTimeoutSeconds, Integer connectTimeoutSeconds,
            String proxyHost, Integer proxyPort, PayloadCacheOptions payloadCacheOptions,
            PayloadCache payloadCache) {
        new URL(url).toURI();
        if (linkedBlockingQueueCapacity != null
                && (linkedBlockingQueueCapacity < 1 || linkedBlockingQueueCapacity > 1000)) {
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
        if (pollIntervalSeconds != null && (pollIntervalSeconds < 1 || pollIntervalSeconds > 600)) {
            throw new IllegalArgumentException("pollIntervalSeconds must be between 1 and 600");
        }
        if (proxyPort != null && (proxyPort < 1 || proxyPort > 65535)) {
            throw new IllegalArgumentException("proxyPort must be between 1 and 65535");
        }
        if (proxyHost != null && proxyPort == null) {
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
}
