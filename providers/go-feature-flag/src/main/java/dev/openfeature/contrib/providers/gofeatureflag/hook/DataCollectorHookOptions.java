package dev.openfeature.contrib.providers.gofeatureflag.hook;

import dev.openfeature.contrib.providers.gofeatureflag.controller.GoFeatureFlagController;
import dev.openfeature.contrib.providers.gofeatureflag.exception.InvalidOptions;
import lombok.Builder;
import lombok.Getter;
import lombok.SneakyThrows;

/**
 * DataCollectorHookOptions is the object containing all the options needed for the Data Collector
 * Hook.
 */
@Builder
@Getter
public class DataCollectorHookOptions {
    /** GoFeatureFlagController is the controller to contact the APIs. */
    private final GoFeatureFlagController gofeatureflagController;
    /**
     * (optional) interval time we publish statistics collection data to the proxy. The parameter is
     * used only if the cache is enabled, otherwise the collection of the data is done directly when
     * calling the evaluation API. default: 1000 ms
     */
    private Long flushIntervalMs;
    /**
     * (optional) max pending events aggregated before publishing for collection data to the proxy.
     * When an event is added while events collection is full, the event is omitted. default: 10000
     */
    private Integer maxPendingEvents;
    /**
     * collectUnCachedEvent (optional) set to true if you want to send all events not only the cached
     * evaluations.
     */
    private Boolean collectUnCachedEvaluation;

    /**
     * Override the builder() method to return our custom builder instead of the Lombok generated
     * builder class.
     *
     * @return a custom builder with validation
     */
    public static DataCollectorHookOptionsBuilder builder() {
        return new CustomBuilder();
    }

    /** used only for the javadoc not to complain. */
    public static class DataCollectorHookOptionsBuilder {}

    /** CustomBuilder is ensuring the validation in the build method. */
    private static class CustomBuilder extends DataCollectorHookOptionsBuilder {
        @SneakyThrows
        public DataCollectorHookOptions build() {
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
