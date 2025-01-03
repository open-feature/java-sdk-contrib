package dev.openfeature.contrib.providers.flipt;

import io.flipt.api.FliptClient.FliptClientBuilder;
import lombok.Builder;
import lombok.Getter;

/** FliptProvider config. */
@Getter
@Builder
public class FliptProviderConfig {
    private FliptClientBuilder fliptClientBuilder;

    @Builder.Default
    private String namespace = "default";
}
