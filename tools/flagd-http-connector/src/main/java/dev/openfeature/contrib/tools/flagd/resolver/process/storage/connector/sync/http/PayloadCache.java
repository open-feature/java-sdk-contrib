package dev.openfeature.contrib.tools.flagd.resolver.process.storage.connector.sync.http;

public interface PayloadCache {
    public void put(String payload);
    public String get();
}
