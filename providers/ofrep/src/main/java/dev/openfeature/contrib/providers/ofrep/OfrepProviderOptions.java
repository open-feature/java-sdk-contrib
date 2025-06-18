package dev.openfeature.contrib.providers.ofrep;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.net.ProxySelector;
import java.time.Duration;
import java.util.concurrent.Executor;
import lombok.Builder;
import lombok.Getter;

/**
 * Options for configuring the OFREP provider.
 */
@Getter
@Builder(builderClassName = "Builder", buildMethodName = "build")
public class OfrepProviderOptions {

    @Builder.Default
    private final String baseUrl = "http://localhost:8016";

    @Builder.Default
    private final ProxySelector proxySelector = ProxySelector.getDefault();

    @Builder.Default
    private final Executor executor = Runnable::run;

    @Builder.Default
    private final Duration timeout = Duration.ofSeconds(10);

    @Builder.Default
    private final ImmutableMap<String, ImmutableList<String>> headers = ImmutableMap.of();
}
