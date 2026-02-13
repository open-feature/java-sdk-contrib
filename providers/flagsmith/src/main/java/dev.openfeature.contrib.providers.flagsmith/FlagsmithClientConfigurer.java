package dev.openfeature.contrib.providers.flagsmith;

import com.flagsmith.FlagsmithClient;
import com.flagsmith.config.FlagsmithCacheConfig;
import com.flagsmith.config.FlagsmithConfig;
import com.flagsmith.config.Retry;
import dev.openfeature.contrib.providers.flagsmith.exceptions.InvalidCacheOptionsException;
import dev.openfeature.contrib.providers.flagsmith.exceptions.InvalidOptionsException;

/**
 * FlagsmithClientConfigurer helps set up and validate the options for the FlagsmithClient used by
 * the FlagsmithProvider class.
 */
public class FlagsmithClientConfigurer {

    /**
     * initializeProvider is initializing the different class element used by the provider.
     *
     * @param options the options used to create the provider
     */
    static FlagsmithClient initializeProvider(FlagsmithProviderOptions options) {

        validateOptions(options);

        FlagsmithClient.Builder flagsmithBuilder = FlagsmithClient.newBuilder();
        // Set main configuration settings
        flagsmithBuilder.setApiKey(options.getApiKey());

        if (options.getHeaders() != null && !options.getHeaders().isEmpty()) {
            flagsmithBuilder.withCustomHttpHeaders(options.getHeaders());
        }

        if (options.getEnvFlagsCacheKey() != null) {
            FlagsmithCacheConfig flagsmithCacheConfig = initializeCacheConfig(options);
            flagsmithBuilder.withCache(flagsmithCacheConfig);
        }

        final FlagsmithConfig flagsmithConfig = initializeConfig(options);
        flagsmithBuilder.withConfiguration(flagsmithConfig);

        return flagsmithBuilder.build();
    }

    /**
     * Sets the cache related configuration for the provider using the FlagsmithCacheConfig builder.
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

        if (options.getExpireCacheAfterWrite() > -1 && options.getExpireCacheAfterWriteTimeUnit() != null) {
            flagsmithCacheConfig.expireAfterAccess(
                    options.getExpireCacheAfterWrite(), options.getExpireCacheAfterWriteTimeUnit());
        }

        if (options.getExpireCacheAfterAccess() > -1 && options.getExpireCacheAfterAccessTimeUnit() != null) {
            flagsmithCacheConfig.expireAfterAccess(
                    options.getExpireCacheAfterAccess(), options.getExpireCacheAfterAccessTimeUnit());
        }

        if (options.getMaxCacheSize() > -1) {
            flagsmithCacheConfig.maxSize(options.getMaxCacheSize());
        }

        if (options.isRecordCacheStats()) {
            flagsmithCacheConfig.recordStats();
        }

        return flagsmithCacheConfig.build();
    }

    /**
     * Set the configuration options for the FlagsmithClient using the FlagsmithConfig builder.
     *
     * @param options The options used to create the provider
     * @return a FlagsmithConfig object with the FlagsmithClient settings
     */
    private static FlagsmithConfig initializeConfig(FlagsmithProviderOptions options) {
        FlagsmithConfig.Builder flagsmithConfig = FlagsmithConfig.newBuilder();

        // Set client level configuration settings
        if (options.getBaseUri() != null) {
            flagsmithConfig.baseUri(options.getBaseUri());
        }

        if (options.getConnectTimeout() > -1) {
            flagsmithConfig.connectTimeout(options.getConnectTimeout());
        }

        if (options.getWriteTimeout() > -1) {
            flagsmithConfig.writeTimeout(options.getWriteTimeout());
        }

        if (options.getReadTimeout() > -1) {
            flagsmithConfig.readTimeout(options.getReadTimeout());
        }

        if (options.getSslSocketFactory() != null && options.getTrustManager() != null) {
            flagsmithConfig.sslSocketFactory(options.getSslSocketFactory(), options.getTrustManager());
        }

        if (options.getHttpInterceptor() != null) {
            flagsmithConfig.addHttpInterceptor(options.getHttpInterceptor());
        }

        if (options.getRetries() > -1) {
            flagsmithConfig.retries(new Retry(options.getRetries()));
        }

        if (options.isLocalEvaluation()) {
            flagsmithConfig.withLocalEvaluation(options.isLocalEvaluation());
        }

        if (options.getEnvironmentRefreshIntervalSeconds() != null) {
            flagsmithConfig.withEnvironmentRefreshIntervalSeconds(options.getEnvironmentRefreshIntervalSeconds());
        }

        if (options.isEnableAnalytics()) {
            flagsmithConfig.withEnableAnalytics(options.isEnableAnalytics());
        }

        if (options.getSupportedProtocols() != null
                && !options.getSupportedProtocols().isEmpty()) {
            flagsmithConfig.withSupportedProtocols(options.getSupportedProtocols());
        }

        return flagsmithConfig.build();
    }

    /**
     * Check the options that have been provided to see if there are any issues. Exceptions will be
     * thrown if there are issues found with the options.
     *
     * @param options the options used to create the provider
     */
    private static void validateOptions(FlagsmithProviderOptions options) {
        if (options == null) {
            throw new InvalidOptionsException("No options provided");
        }

        if (options.getApiKey() == null || options.getApiKey().isEmpty()) {
            throw new InvalidOptionsException("Flagsmith API key has not been set.");
        }

        if (options.getEnvFlagsCacheKey() == null
                && (options.getExpireCacheAfterWrite() > -1
                        || options.getExpireCacheAfterAccess() > -1
                        || options.getMaxCacheSize() > -1
                        || options.isRecordCacheStats())) {
            throw new InvalidCacheOptionsException(
                    "No Flagsmith cache key provided but other cache settings have been set.");
        }
    }
}
