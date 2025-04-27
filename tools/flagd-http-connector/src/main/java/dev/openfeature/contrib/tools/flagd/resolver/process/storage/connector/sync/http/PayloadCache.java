package dev.openfeature.contrib.tools.flagd.resolver.process.storage.connector.sync.http;

public interface PayloadCache {
    void put(String payload);
    String get();
}
