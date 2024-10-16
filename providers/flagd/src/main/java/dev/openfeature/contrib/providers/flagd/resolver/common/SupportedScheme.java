package dev.openfeature.contrib.providers.flagd.resolver.common;

import lombok.Getter;

@Getter
enum SupportedScheme {
    ENVOY("envoy"), DNS("dns"), XDS("xds"), UDS("uds");

    private final String scheme;

    SupportedScheme(String scheme) {
        this.scheme = scheme;
    }
}
