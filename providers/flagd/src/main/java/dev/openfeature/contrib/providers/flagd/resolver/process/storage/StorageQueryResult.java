package dev.openfeature.contrib.providers.flagd.resolver.process.storage;

import dev.openfeature.contrib.providers.flagd.resolver.process.model.FeatureFlag;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Map;
import lombok.Getter;

/**
 * To be returned by the storage when a flag is queried. Contains the flag (iff a flag associated with the given key
 * exists, null otherwise) and global flag metadata
 */
@Getter
@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP"},
        justification = "The storage provides access to both feature flags and global metadata")
public class StorageQueryResult {
    private final FeatureFlag featureFlag;
    private final Map<String, Object> globalFlagMetadata;

    public StorageQueryResult(FeatureFlag featureFlag, Map<String, Object> globalFlagMetadata) {
        this.featureFlag = featureFlag;
        this.globalFlagMetadata = globalFlagMetadata;
    }
}
