package dev.openfeature.contrib.providers.flagsmith;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import lombok.Builder;
import lombok.Getter;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;
import okhttp3.Interceptor;

/**
 * FlagsmithProviderOptions contains the options to initialise the Flagsmith provider.
 */
@SuppressFBWarnings(value = { "EI_EXPOSE_REP", "EI_EXPOSE_REP2" }, justification = "The headers need to be mutable")
@Builder
@Getter
class FlagsmithProviderOptions {

    /**
     * Your API Token.
     * Note that this is either the `Environment API` key or the `Server Side SDK Token`
     * depending on if you are using Local or Remote Evaluation
     * Required.
     */
    private String apiKey;

    /**
     * Add custom headers which will be sent with each network request
     * to the Flagsmith API.
     * Optional.
     * Defaults to no custom headers.
     */
    private HashMap<String, String> headers;

    // Cache based properties
    /**
     * Enable in-memory caching for the Flagsmith API.
     * Optional.
     * Default: not caching anything.
     */
    private String envFlagsCacheKey;

    private TimeUnit expireCacheAfterWriteTimeUnit;

    private int expireCacheAfterWrite;

    private TimeUnit expireCacheAfterAccessTimeUnit;

    private int expireCacheAfterAccess;

    private int maxCacheSize;

    private boolean recordCacheStats;

    /**
     * Override the default Flagsmith API URL if you are self-hosting.
     * Optional.
     * Default: https://edge.api.flagsmith.com/api/v1/
     */
    private String baseUri;

    /**
     * The network timeout in milliseconds.
     * See https://square.github.io/okhttp/4.x/okhttp/okhttp3/ for details
     * Defaults: 2000
     */
    private int connectTimeout;

    /**
     * The network timeout in milliseconds when writing.
     * See https://square.github.io/okhttp/4.x/okhttp/okhttp3/ for details
     * Defaults: 5000
     */
    private int writeTimeout;

    /**
     * The network timeout in milliseconds when reading.
     * See https://square.github.io/okhttp/4.x/okhttp/okhttp3/ for details
     * Defaults: 5000
     */
    private int readTimeout;

    /**
     * Override the sslSocketFactory
     * See https://square.github.io/okhttp/4.x/okhttp/okhttp3/ for details
     * Optional, must include trustManager if provided.
     */
    private SSLSocketFactory sslSocketFactory;

    /**
     * X509TrustManager used when overriding the sslSocketFactory
     * See https://square.github.io/okhttp/4.x/okhttp/okhttp3/ for details
     * Optional, must include sslSocketFactory if provided.
     */
    private X509TrustManager trustManager;

    /**
     * Add a custom HTTP interceptor in the form of an okhttp3.Interceptor object.
     * Optional.
     */
    private Interceptor httpInterceptor;

    /**
     * Add a custom com.flagsmith.config.Retry object to configure the
     * backoff / retry configuration.
     * Optional.
     * Default: 3
     */
    private int retries;

    /**
     * Controls which mode to run in; local or remote evaluation.
     * Optional.
     * Default: false.
     */
    private boolean localEvaluation;

    /**
     * Set environment refresh rate with polling manager.
     * Only needed when local evaluation is true.
     * Optional.
     * Default: 60
     */
    private int environmentRefreshIntervalSeconds;

    /**
     * Controls whether Flag Analytics data is sent to the Flagsmith API
     * See https://docs.flagsmith.com/advanced-use/flag-analytics.
     * Optional.
     * Default: false
     */
    private boolean enableAnalytics;
}