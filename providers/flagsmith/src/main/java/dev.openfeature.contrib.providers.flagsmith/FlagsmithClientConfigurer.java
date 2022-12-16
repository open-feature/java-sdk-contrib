package dev.openfeature.contrib.providers.flagsmith;

import com.flagsmith.FlagsmithClient;
import com.flagsmith.config.FlagsmithCacheConfig;
import com.flagsmith.config.Retry;
import dev.openfeature.contrib.providers.flagsmith.exceptions.InvalidOptions;

/**
 * FlagsmithClientConfigurer helps set up and validate the options for the FlagsmithClient
 * used by the FlagsmithProvider class
 */
public class FlagsmithClientConfigurer {

    /**
     * Check the options that have been provided to see if there are any issues.
     * Exceptions will be thrown if there are issues found with the options.
     *
     * @param options the options used to create the provider
     */
    public static void validateOptions(FlagsmithProviderOptions options) {
        if (options.getApiKey() == null) {
            throw new InvalidOptions("Flagsmith API key has not been set.");
        }

        if (options.getEnvFlagsCacheKey() == null
            && (options.getExpireCacheAfterWrite() != 0
            || options.getExpireCacheAfterAccess() != 0
            || options.getMaxCacheSize() != 0)) {
            throw new InvalidOptions(
                "No Flagsmith cache key provided but other cache settings have been set."
            );
        }
    }

    /**
     * initializeProvider is initializing the different class element used by the provider.
     *
     * @param options the options used to create the provider
     */
    static FlagsmithClient initializeProvider(FlagsmithProviderOptions options) {
        FlagsmithClient.Builder flagsmithBuilder = FlagsmithClient
            .newBuilder();

        // Set main configuration settings
        if (options.getApiKey() != null) {
            flagsmithBuilder.setApiKey(options.getApiKey());
        }

        if (options.getHeaders() != null && !options.getHeaders().isEmpty()) {
            flagsmithBuilder.withCustomHttpHeaders(options.getHeaders());
        }

        if (options.getEnvFlagsCacheKey() != null) {
            FlagsmithCacheConfig flagsmithCacheConfig = initializeCacheConfig(options);
            flagsmithBuilder.withCache(flagsmithCacheConfig);
        }

        com.flagsmith.config.FlagsmithConfig flagsmithConfig = initializeConfig(options);
        flagsmithBuilder.withConfiguration(flagsmithConfig);

        return flagsmithBuilder.build();
    }

    /**
     * Sets the cache related configuration for the provider using
     * the FlagsmithCacheConfig builder.
     *
     * @param options the options used to create the provider
     * @return a FlagsmithCacheConfig object containing the FlagsmithClient cache options
     */
    private static FlagsmithCacheConfig initializeCacheConfig(FlagsmithProviderOptions options) {
        FlagsmithCacheConfig.Builder flagsmithCacheConfig = FlagsmithCacheConfig.newBuilder();

        // Set cache configuration settings
        if (options.getEnvFlagsCacheKey() != null) {
            flagsmithCacheConfig.enableEnvLevelCaching(options.getEnvFlagsCacheKey());
        }

        if (options.getExpireCacheAfterWrite() != 0
            && options.getExpireCacheAfterWriteTimeUnit() != null) {
            flagsmithCacheConfig.expireAfterAccess(
                options.getExpireCacheAfterWrite(),
                options.getExpireCacheAfterWriteTimeUnit());
        }

        if (options.getExpireCacheAfterAccess() != 0
            && options.getExpireCacheAfterAccessTimeUnit() != null) {
            flagsmithCacheConfig.expireAfterAccess(
                options.getExpireCacheAfterAccess(),
                options.getExpireCacheAfterAccessTimeUnit());
        }

        if (options.getMaxCacheSize() != 0) {
            flagsmithCacheConfig.maxSize(options.getMaxCacheSize());
        }

        return flagsmithCacheConfig.build();
    }

    /**
     * Set the configuration options for the FlagsmithClient using
     * the FlagsmithConfig builder.
     *
     * @param options The options used to create the provider
     * @return a FlagsmithConfig object with the FlagsmithClient settings
     */
    private static com.flagsmith.config.FlagsmithConfig initializeConfig(
        FlagsmithProviderOptions options) {
        com.flagsmith.config.FlagsmithConfig.Builder flagsmithConfig = com.flagsmith.config.FlagsmithConfig
            .newBuilder();

        // Set client level configuration settings
        if (options.getBaseUri() != null) {
            flagsmithConfig.baseUri(options.getBaseUri());
        }

        if (options.getConnectTimeout() != 0) {
            flagsmithConfig.connectTimeout(options.getConnectTimeout());
        }

        if (options.getWriteTimeout() != 0) {
            flagsmithConfig.writeTimeout(options.getWriteTimeout());
        }

        if (options.getReadTimeout() != 0) {
            flagsmithConfig.readTimeout(options.getReadTimeout());
        }

        if (options.getSslSocketFactory() != null && options.getTrustManager() != null) {
            flagsmithConfig
                .sslSocketFactory(options.getSslSocketFactory(), options.getTrustManager());
        }

        if (options.getHttpInterceptor() != null) {
            flagsmithConfig.addHttpInterceptor(options.getHttpInterceptor());
        }

        if (options.getRetries() != 0) {
            flagsmithConfig.retries(new Retry(options.getRetries()));
        }

        if (options.isLocalEvaluation()) {
            flagsmithConfig.withLocalEvaluation(options.isLocalEvaluation());
        }

        if (options.getEnvironmentRefreshIntervalSeconds() != 0) {
            flagsmithConfig.withEnvironmentRefreshIntervalSeconds(options
                .getEnvironmentRefreshIntervalSeconds());
        }

        if (options.isEnableAnalytics()) {
            flagsmithConfig.withEnableAnalytics(options.isEnableAnalytics());
        }

        return flagsmithConfig.build();
    }
}
