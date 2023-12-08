package dev.openfeature.contrib.providers.flagd.resolver.grpc.cache;

import lombok.Getter;

/**
 * Defines which type of cache to use.
 */
@Getter
public enum CacheType {
    DISABLED("disabled"),
    LRU("lru");

    private final String value;

    CacheType(String value) {
        this.value = value;
    }
}
