package dev.openfeature.contrib.providers.flipt;

import com.flipt.api.FliptApiClientBuilder;
import lombok.Builder;
import lombok.Getter;


/**
 * FliptProvider config.
 */
@Getter
@Builder
public class FliptProviderConfig {
    private FliptApiClientBuilder fliptApiClientBuilder;

    @Builder.Default
    private String namespace = "default";
}
