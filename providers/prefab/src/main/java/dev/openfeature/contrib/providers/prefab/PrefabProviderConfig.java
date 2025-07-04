package dev.openfeature.contrib.providers.prefab;

import cloud.prefab.client.Options;
import lombok.Builder;
import lombok.Getter;

/**
 * Options for initializing prefab provider.
 */
@Getter
@Builder
public class PrefabProviderConfig {
    private Options options;
}
