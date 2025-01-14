package dev.openfeature.contrib.providers.flagd.resolver.process;

import dev.openfeature.contrib.providers.flagd.resolver.process.model.FeatureFlag;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.Storage;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.StorageQueryResult;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.StorageStateChange;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import javax.annotation.Nullable;

public class MockStorage implements Storage {

    private final Map<String, FeatureFlag> mockFlags;
    private final Map<String, Object> flagSetMetadata;
    private final BlockingQueue<StorageStateChange> mockQueue;

    public MockStorage(Map<String, FeatureFlag> mockFlags, Map<String, Object> flagSetMetadata) {
        this.mockFlags = mockFlags;
        this.mockQueue = null;
        this.flagSetMetadata = flagSetMetadata;
    }

    public MockStorage(Map<String, FeatureFlag> mockFlags, BlockingQueue<StorageStateChange> mockQueue) {
        this.mockFlags = mockFlags;
        this.mockQueue = mockQueue;
        this.flagSetMetadata = Collections.emptyMap();
    }

    public MockStorage(Map<String, FeatureFlag> flagMap) {
        this.mockFlags = flagMap;
        this.mockQueue = null;
        this.flagSetMetadata = Collections.emptyMap();
    }

    public void init() {
        // no-op
    }

    public void shutdown() {
        // no-op
    }

    @Override
    public StorageQueryResult getFlag(String key) {
        return new StorageQueryResult(mockFlags.get(key), flagSetMetadata);
    }

    @Nullable public BlockingQueue<StorageStateChange> getStateQueue() {
        return mockQueue;
    }
}
