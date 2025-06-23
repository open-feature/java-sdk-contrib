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
 */
@Getter
@Builder(builderClassName = "Builder", buildMethodName = "build")
public class OfrepProviderOptions {

    private static final int DEFAULT_THREAD_POOL_SIZE = 5;

    @Builder.Default
    private final String baseUrl = "http://localhost:8016";

    @Builder.Default
    private final ProxySelector proxySelector = ProxySelector.getDefault();

    @Builder.Default
    private final Executor executor = Executors.newFixedThreadPool(DEFAULT_THREAD_POOL_SIZE);

    @Builder.Default
    private final Duration requestTimeout = Duration.ofSeconds(10);

    @Builder.Default
    private final Duration connectTimeout = Duration.ofSeconds(10);

    @Builder.Default
    private final ImmutableMap<String, ImmutableList<String>> headers = ImmutableMap.of();
}
