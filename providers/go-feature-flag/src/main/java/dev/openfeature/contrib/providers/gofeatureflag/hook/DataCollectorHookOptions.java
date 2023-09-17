package dev.openfeature.contrib.providers.gofeatureflag.hook;

import com.google.common.cache.CacheBuilder;
import dev.openfeature.sdk.ProviderEvaluation;
import lombok.Builder;
import lombok.Getter;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

@Builder
@Getter
public class DataCollectorHookOptions {
    /**
     * httpClient is the instance of the OkHttpClient used by the provider
    */
    private OkHttpClient httpClient;
    /**
     * (mandatory) parsedEndpoint contains the DNS of your GO Feature Flag relay proxy
     * example: https://mydomain.com/gofeatureflagproxy/
     */
    private HttpUrl parsedEndpoint;
    /**
     * (optional) If the relay proxy is configured to authenticate the requests, you should provide
     * an API Key to the provider.
     * Please ask the administrator of the relay proxy to provide an API Key.
     * (This feature is available only if you are using GO Feature Flag relay proxy v1.7.0 or above)
     * Default: null
     */
    private String apiKey;
    /**
     * (optional) interval time we publish statistics collection data to the proxy.
     * The parameter is used only if the cache is enabled, otherwise the collection of the data is done directly
     * when calling the evaluation API.
     * default: 1000 ms
     */
    private Long flushIntervalMs;
    /**
      * (optional) max pending events aggregated before publishing for collection data to the proxy.
      * When event is added while events collection is full, event is omitted.
      * default: 10000
      */
    private Integer maxPendingEvents;
    /**
     * collectUnCachedEvent (optional) set to true if you want to send all events not only the cached evaluations.
     */
    private Boolean collectUnCachedEvaluation;
}
