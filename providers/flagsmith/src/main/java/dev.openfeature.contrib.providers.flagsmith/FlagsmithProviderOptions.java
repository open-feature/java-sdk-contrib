package dev.openfeature.contrib.providers.flagsmith;

import com.flagsmith.config.FlagsmithConfig.Protocol;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;
import lombok.Builder;
import lombok.Getter;
import okhttp3.Interceptor;

/** FlagsmithProviderOptions contains the options to initialise the Flagsmith provider. */
@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP"},
        justification = "The headers need to be mutable")
@Builder(toBuilder = true)
@Getter
public class FlagsmithProviderOptions {

    /**
     * Your API Token. Note that this is either the `Environment API` key or the `Server Side SDK
     * Token` depending on if you are using Local or Remote Evaluation Required.
     */
    private String apiKey;

    /**
     * Add custom headers which will be sent with each network request to the Flagsmith API. Optional.
     * Defaults to no custom headers.
     */
    private HashMap<String, String> headers;

    // Cache based properties
    /** Enable in-memory caching for the Flagsmith API. Optional. Default: not caching anything. */
    private String envFlagsCacheKey;

    /** The time unit used for cache expiry after write. Optional Default: TimeUnit.MINUTES */
    private TimeUnit expireCacheAfterWriteTimeUnit;

    /** The integer time for cache expiry after write. Optional Default: -1 */
    @Builder.Default
    private int expireCacheAfterWrite = -1;

    /** The time unit used for cache expiry after reading. Optional Default: TimeUnit.MINUTES */
    private TimeUnit expireCacheAfterAccessTimeUnit;

    /** The integer time for cache expiry after reading. Optional Default: -1 */
    @Builder.Default
    private int expireCacheAfterAccess = -1;

    /** The maximum size of the cache in MB. Optional Default: -1 */
    @Builder.Default
    private int maxCacheSize = -1;

    /** Whether cache statistics should be recorded. Optional Default: false */
    @Builder.Default
    private boolean recordCacheStats = false;

    /**
     * Override the default Flagsmith API URL if you are self-hosting. Optional. Default:
     * https://edge.api.flagsmith.com/api/v1/
     */
    @Builder.Default
    private String baseUri = "https://edge.api.flagsmith.com/api/v1/";

    /**
     * The network timeout in milliseconds. See https://square.github.io/okhttp/4.x/okhttp/okhttp3/
     * for details Defaults: 2000
     */
    @Builder.Default
    private int connectTimeout = 2000;

    /**
     * The network timeout in milliseconds when writing. See
     * https://square.github.io/okhttp/4.x/okhttp/okhttp3/ for details Defaults: 5000
     */
    @Builder.Default
    private int writeTimeout = 5000;

    /**
     * The network timeout in milliseconds when reading. See
     * https://square.github.io/okhttp/4.x/okhttp/okhttp3/ for details Defaults: 5000
     */
    @Builder.Default
    private int readTimeout = 5000;

    /**
     * Override the sslSocketFactory See https://square.github.io/okhttp/4.x/okhttp/okhttp3/ for
     * details Optional, must include trustManager if provided.
     */
    private SSLSocketFactory sslSocketFactory;

    /**
     * X509TrustManager used when overriding the sslSocketFactory See
     * https://square.github.io/okhttp/4.x/okhttp/okhttp3/ for details Optional, must include
     * sslSocketFactory if provided.
     */
    private X509TrustManager trustManager;

    /** Add a custom HTTP interceptor in the form of an okhttp3.Interceptor object. Optional. */
    private Interceptor httpInterceptor;

    /**
     * Add a custom com.flagsmith.config.Retry object to configure the backoff / retry configuration.
     * Optional. Default: 3
     */
    @Builder.Default
    private int retries = 3;

    /** Controls which mode to run in; local or remote evaluation. Optional. Default: false. */
    @Builder.Default
    private boolean localEvaluation = false;

    /**
     * Set environment refresh rate with polling manager. Only needed when local evaluation is true.
     * Optional. Default: 60
     */
    @Builder.Default
    private Integer environmentRefreshIntervalSeconds = 60;

    /**
     * Controls whether Flag Analytics data is sent to the Flagsmith API See
     * https://docs.flagsmith.com/advanced-use/flag-analytics. Optional. Default: false
     */
    @Builder.Default
    private boolean enableAnalytics = false;

    /**
     * Determines whether to resolve a feature value as a boolean or use the isFeatureEnabled as the
     * flag itself. These values will be false and true respectively. Optional. Default: false
     */
    @Builder.Default
    private boolean usingBooleanConfigValue = false;

    /**
     * Set the list of supported protocols that should be used. Optional. Default: All the enum
     * protocols from FlagsmithConfig.
     */
    @Builder.Default
    private List<Protocol> supportedProtocols = Arrays.stream(Protocol.values()).collect(Collectors.toList());
}
