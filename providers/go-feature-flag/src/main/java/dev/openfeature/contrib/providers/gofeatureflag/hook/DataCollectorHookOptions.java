package dev.openfeature.contrib.providers.gofeatureflag.hook;

import dev.openfeature.contrib.providers.gofeatureflag.exception.InvalidEndpoint;
import dev.openfeature.contrib.providers.gofeatureflag.exception.InvalidOptions;
import lombok.Builder;
import lombok.Getter;
import lombok.SneakyThrows;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

/**
 * DataCollectorHookOptions is the object containing all the options needed for the Data Collector Hook.
 */
@Builder
@Getter
public class DataCollectorHookOptions {
    /**
     * httpClient is the instance of the OkHttpClient used by the provider.
     */
    private OkHttpClient httpClient;
    /**
     * (mandatory) parsedEndpoint contains the DNS of your GO Feature Flag relay proxy.
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

    /**
     * Override the builder() method to return our custom builder instead of the Lombok generated builder class.
     *
     * @return a custom builder with validation
     */
    public static DataCollectorHookOptionsBuilder builder() {
        return new CustomBuilder();
    }

    public static class DataCollectorHookOptionsBuilder {
    }

    /**
     * CustomBuilder is ensuring the validation in the build method.
     */
    private static class CustomBuilder extends DataCollectorHookOptionsBuilder {
        @SneakyThrows
        public DataCollectorHookOptions build() {
            if (super.parsedEndpoint == null) {
                throw new InvalidEndpoint("endpoint is a mandatory field when creating the hook");
            }
            if (super.flushIntervalMs != null && super.flushIntervalMs <= 0) {
                throw new InvalidOptions("flushIntervalMs must be larger than 0");
            }
            if (super.maxPendingEvents != null && super.maxPendingEvents <= 0) {
                throw new InvalidOptions("maxPendingEvents must be larger than 0");
            }
            return super.build();
        }
    }
}
