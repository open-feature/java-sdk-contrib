package dev.openfeature.contrib.providers.gofeatureflag;

import dev.openfeature.contrib.providers.gofeatureflag.bean.EvaluationType;
import dev.openfeature.contrib.providers.gofeatureflag.exception.InvalidEndpoint;
import dev.openfeature.contrib.providers.gofeatureflag.exception.InvalidExporterMetadata;
import dev.openfeature.contrib.providers.gofeatureflag.exception.InvalidOptions;
import dev.openfeature.contrib.providers.gofeatureflag.util.Const;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import lombok.val;

/**
 * GoFeatureFlagProviderOptions contains the options to initialise the provider.
 *
 * <p>Every optional field is unset by default, and its getter resolves the documented default value,
 * so the provider and the evaluators can read the options without repeating the fallbacks.</p>
 */
@Builder
@Getter
public class GoFeatureFlagProviderOptions {
    /** Default timeout in millisecond when calling the GO Feature Flag relay proxy API. */
    private static final int DEFAULT_TIMEOUT_MS = 10000;
    /** Default maximum number of connexions in the connexion pool. */
    private static final int DEFAULT_MAX_IDLE_CONNECTIONS = 1000;
    /** Default time in millisecond we keep the connexion open (2 hours). */
    private static final long DEFAULT_KEEP_ALIVE_DURATION_MS = 7200000L;

    /**
     * evaluationType is the type of evaluation you want to use.
     * - If you want to have a local evaluation, you should use IN_PROCESS.
     * - If you want to have an evaluation on the relay-proxy directly, you should use REMOTE.
     * Default: IN_PROCESS
     */
    private EvaluationType evaluationType;
    /**
     * (mandatory) endpoint contains the DNS of your GO Feature Flag relay proxy. example:
     * https://mydomain.com/gofeatureflagproxy/
     */
    private String endpoint;
    /**
     * (optional) timeout in millisecond we are waiting when calling the go-feature-flag relay proxy
     * API. Default: 10000 ms
     */
    private int timeout;
    /**
     * (optional) maxIdleConnections is the maximum number of connexions in the connexion pool.
     * Default: 1000
     *
     * <p>This option is currently not used by the provider, the underlying HTTP client does not
     * expose this setting.</p>
     */
    private int maxIdleConnections;
    /**
     * (optional) keepAliveDuration is the time in millisecond we keep the connexion open. Default:
     * 7200000 (2 hours)
     *
     * <p>This option is currently not used by the provider, the underlying HTTP client does not
     * expose this setting.</p>
     */
    private Long keepAliveDuration;
    /**
     * (optional) If the relay proxy is configured to authenticate the requests, you should provide an
     * API Key to the provider. Please ask the administrator of the relay proxy to provide an API Key.
     * (This feature is available only if you are using GO Feature Flag relay proxy v1.7.0 or above)
     * Default: null
     */
    private String apiKey;
    /**
     * (optional) interval time we publish statistics collection data to the proxy. The parameter is
     * used only if the cache is enabled, otherwise the collection of the data is done directly when
     * calling the evaluation API. default: 60000 ms (1 minute)
     */
    private Long flushIntervalMs;
    /**
     * (optional) max pending events aggregated before publishing for collection data to the proxy.
     * When an event is added while an events collection is full, the event is omitted. default: 10000
     */
    private Integer maxPendingEvents;
    /**
     * (optional) disableDataCollection set to true if you don't want to collect the usage of flags
     * retrieved in the cache. default: false
     */
    private boolean disableDataCollection;

    /**
     * (optional) exporterMetadata is the metadata we send to the GO Feature Flag relay proxy when we report the
     * evaluation data usage. default: empty
     */
    private Map<String, Object> exporterMetadata;

    /**
     * (optional) If you are using in process evaluation, by default we will load in memory all the flags available
     * in the relay proxy. If you want to limit the number of flags loaded in memory, you can use this parameter.
     * By setting this parameter, you will only load the flags available in the list.
     *
     * <p>If null or empty, all the flags available in the relay proxy will be loaded.</p>
     */
    private List<String> evaluationFlagList;

    /**
     * (optional) interval time we poll the proxy to check if the configuration has changed. If the
     * cache is enabled, we will poll the relay-proxy every X milliseconds to check if the
     * configuration has changed. default: 120000
     */
    private Long flagChangePollingIntervalMs;

    /**
     * (optional) Number of WASM instances kept in the evaluation pool for in-process evaluation.
     * Each instance owns independent WASM linear memory, allowing fully concurrent evaluations
     * without serialisation. Must be &gt;= 1 when set explicitly.
     * Default: number of available CPU cores.
     */
    private Integer wasmEvaluatorPoolSize;

    /**
     * Get the type of evaluation to use.
     *
     * @return the configured evaluation type, IN_PROCESS if none was set
     */
    public EvaluationType getEvaluationType() {
        return evaluationType == null ? EvaluationType.IN_PROCESS : evaluationType;
    }

    /**
     * Get the timeout in millisecond when calling the GO Feature Flag relay proxy API.
     *
     * @return the configured timeout, 10000 ms if none was set
     */
    public int getTimeout() {
        return timeout == 0 ? DEFAULT_TIMEOUT_MS : timeout;
    }

    /**
     * Get the maximum number of connexions in the connexion pool.
     *
     * @return the configured maximum, 1000 if none was set
     */
    public int getMaxIdleConnections() {
        return maxIdleConnections == 0 ? DEFAULT_MAX_IDLE_CONNECTIONS : maxIdleConnections;
    }

    /**
     * Get the time in millisecond we keep the connexion open.
     *
     * @return the configured duration, 7200000 ms if none was set
     */
    public Long getKeepAliveDuration() {
        return keepAliveDuration == null ? DEFAULT_KEEP_ALIVE_DURATION_MS : keepAliveDuration;
    }

    /**
     * Get the interval time we publish statistics collection data to the proxy.
     *
     * @return the configured interval, 60000 ms if none was set
     */
    public Long getFlushIntervalMs() {
        return flushIntervalMs == null ? Const.DEFAULT_FLUSH_INTERVAL_MS : flushIntervalMs;
    }

    /**
     * Get the maximum number of events aggregated before publishing them to the proxy.
     *
     * @return the configured maximum, 10000 if none was set
     */
    public Integer getMaxPendingEvents() {
        return maxPendingEvents == null ? Const.DEFAULT_MAX_PENDING_EVENTS : maxPendingEvents;
    }

    /**
     * Get the metadata sent to the relay proxy when reporting the evaluation data usage.
     *
     * @return the configured metadata, an empty map if none was set
     */
    public Map<String, Object> getExporterMetadata() {
        return exporterMetadata == null ? Collections.emptyMap() : exporterMetadata;
    }

    /**
     * Get the list of flags to load for in process evaluation.
     *
     * @return the configured list, an empty list if none was set, meaning all the flags are loaded
     */
    public List<String> getEvaluationFlagList() {
        return evaluationFlagList == null ? Collections.emptyList() : evaluationFlagList;
    }

    /**
     * Get the interval time we poll the proxy to check if the configuration has changed.
     *
     * @return the configured interval, 120000 ms if none was set
     */
    public Long getFlagChangePollingIntervalMs() {
        return flagChangePollingIntervalMs == null
                ? Const.DEFAULT_POLLING_CONFIG_FLAG_CHANGE_INTERVAL_MS
                : flagChangePollingIntervalMs;
    }

    /**
     * Get the number of WASM instances kept in the evaluation pool.
     *
     * @return the configured pool size, the number of available CPU cores if none was set
     */
    public Integer getWasmEvaluatorPoolSize() {
        return wasmEvaluatorPoolSize == null ? Const.DEFAULT_WASM_EVALUATOR_POOL_SIZE : wasmEvaluatorPoolSize;
    }

    /**
     * Validate the options provided to the provider.
     *
     * <p>Validation reads the fields directly and not the getters, to check what the caller has
     * really set and not the resolved default values.</p>
     *
     * @throws InvalidOptions - if options are invalid
     */
    public void validate() throws InvalidOptions {
        if (endpoint == null || endpoint.isEmpty()) {
            throw new InvalidEndpoint("endpoint is a mandatory field when initializing the provider");
        }

        try {
            new URL(endpoint);
        } catch (MalformedURLException e) {
            throw new InvalidEndpoint("malformed endpoint: " + endpoint);
        }

        if (wasmEvaluatorPoolSize != null && wasmEvaluatorPoolSize < 1) {
            throw new InvalidOptions("wasmEvaluatorPoolSize must be at least 1");
        }

        if (exporterMetadata != null) {
            val acceptableExporterMetadataTypes = List.of("String", "Boolean", "Integer", "Double");
            for (Map.Entry<String, Object> entry : exporterMetadata.entrySet()) {
                if (!acceptableExporterMetadataTypes.contains(
                        entry.getValue().getClass().getSimpleName())) {
                    throw new InvalidExporterMetadata(
                            "exporterMetadata can only contain String, Boolean, Integer or Double");
                }
            }
        }
    }
}
