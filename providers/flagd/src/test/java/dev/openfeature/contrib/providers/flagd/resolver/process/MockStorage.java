package dev.openfeature.contrib.providers.flagd.resolver.process;

import dev.openfeature.contrib.providers.flagd.resolver.process.model.FeatureFlag;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.Storage;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.StorageStateChange;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import javax.annotation.Nullable;

public class MockStorage implements Storage {

    private final Map<String, FeatureFlag> mockFlags;
    private final BlockingQueue<StorageStateChange> mockQueue;

    public MockStorage(Map<String, FeatureFlag> mockFlags, BlockingQueue<StorageStateChange> mockQueue) {
        this.mockFlags = mockFlags;
        this.mockQueue = mockQueue;
    }

    public MockStorage(Map<String, FeatureFlag> flagMap) {
        this.mockFlags = flagMap;
        this.mockQueue = null;
    }

    public void init() {
        // no-op
    }

    public void shutdown() {
        // no-op
    }

    public FeatureFlag getFlag(String key) {
        return mockFlags.get(key);
    }

    @Nullable public BlockingQueue<StorageStateChange> getStateQueue() {
        return mockQueue;
    }
}
