package dev.openfeature.contrib.providers.ofrep;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.net.ProxySelector;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import lombok.Builder;
import lombok.Getter;

/**
 * Options for configuring the OFREP provider.
 *
 * <p>{@code baseUrl}, {@code requestTimeout} and {@code headers} default to the {@code OFREP_ENDPOINT},
 * {@code OFREP_TIMEOUT_MS} and {@code OFREP_HEADERS} environment variables when they are set. Values set on
 * the builder take precedence.
 */
@Getter
@Builder(builderClassName = "Builder", buildMethodName = "build")
public class OfrepProviderOptions {

    private static final int DEFAULT_THREAD_POOL_SIZE = 5;

    @Builder.Default
    private final String baseUrl = OfrepEnvironment.baseUrl(System::getenv);

    @Builder.Default
    private final ProxySelector proxySelector = ProxySelector.getDefault();

    @Builder.Default
    private final Executor executor = Executors.newFixedThreadPool(DEFAULT_THREAD_POOL_SIZE);

    @Builder.Default
    private final Duration requestTimeout = OfrepEnvironment.requestTimeout(System::getenv);

    @Builder.Default
    private final Duration connectTimeout = Duration.ofSeconds(10);

    @Builder.Default
    private final ImmutableMap<String, ImmutableList<String>> headers = OfrepEnvironment.headers(System::getenv);
}
