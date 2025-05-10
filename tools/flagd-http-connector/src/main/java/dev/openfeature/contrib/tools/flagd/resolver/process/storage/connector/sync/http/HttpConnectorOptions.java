package dev.openfeature.contrib.tools.flagd.resolver.process.storage.connector.sync.http;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * Represents configuration options for the HTTP connector.
 */
@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "CT_CONSTRUCTOR_THROW"},
        justification = "builder validations")
@Slf4j
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

    @Builder.Default
    private Boolean useFailsafeCache;

    @Builder.Default
    private Boolean usePollingCache;

    @NonNull private String url;

    /**
     * HttpConnectorOptions constructor.
     */
    @Builder
    public HttpConnectorOptions(
            Integer pollIntervalSeconds,
            Integer linkedBlockingQueueCapacity,
            Integer scheduledThreadPoolSize,
            Integer requestTimeoutSeconds,
            Integer connectTimeoutSeconds,
            String url,
            Map<String, String> headers,
            ExecutorService httpClientExecutor,
            String proxyHost,
            Integer proxyPort,
            PayloadCacheOptions payloadCacheOptions,
            PayloadCache payloadCache,
            Boolean useHttpCache,
            Boolean useFailsafeCache,
            Boolean usePollingCache) {
        validate(
                url,
                pollIntervalSeconds,
                linkedBlockingQueueCapacity,
                scheduledThreadPoolSize,
                requestTimeoutSeconds,
                connectTimeoutSeconds,
                proxyHost,
                proxyPort,
                payloadCacheOptions,
                payloadCache,
                useFailsafeCache,
                usePollingCache);
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
        if (useFailsafeCache != null) {
            this.useFailsafeCache = useFailsafeCache;
        }
        if (usePollingCache != null) {
            this.usePollingCache = usePollingCache;
        }
    }

    @SneakyThrows
    private void validate(
            String url,
            Integer pollIntervalSeconds,
            Integer linkedBlockingQueueCapacity,
            Integer scheduledThreadPoolSize,
            Integer requestTimeoutSeconds,
            Integer connectTimeoutSeconds,
            String proxyHost,
            Integer proxyPort,
            PayloadCacheOptions payloadCacheOptions,
            PayloadCache payloadCache,
            Boolean useFailsafeCache,
            Boolean usePollingCache) {
        validateUrl(url);
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
        if ((Boolean.TRUE.equals(useFailsafeCache) || Boolean.TRUE.equals(usePollingCache)) && payloadCache == null) {
            throw new IllegalArgumentException(
                    "payloadCache must be set if useFailsafeCache or usePollingCache is set");
        }

        if (payloadCache != null && Boolean.TRUE.equals(usePollingCache)) {

            // verify payloadCache overrides put(String key, String payload, int ttlSeconds)
            boolean overridesTtlPutMethod = false;
            try {
                Method method = payloadCache.getClass().getMethod("put", String.class, String.class, int.class);
                // Check if the method is declared in the class and not inherited
                overridesTtlPutMethod = method.getDeclaringClass() != PayloadCache.class;
            } catch (NoSuchMethodException e) {
                log.debug("payloadCache does not override put(String key, String payload, int ttlSeconds)");
            }
            if (!overridesTtlPutMethod) {
                throw new IllegalArgumentException("when usePollingCache is used, payloadCache must override "
                        + "put(String key, String payload, int ttlSeconds)");
            }
        }
    }

    private static void validateUrl(String url) throws URISyntaxException, MalformedURLException {
        new URL(url).toURI();
    }
}
