package dev.openfeature.contrib.providers.configcat;

import com.configcat.ConfigCatClient;
import lombok.Builder;
import lombok.Getter;

import java.util.function.Consumer;


/**
 * Options for initializing ConfigCat provider.
 */
@Getter
@Builder
public class ConfigCatProviderConfig {
    private Consumer<ConfigCatClient.Options> options;

    // Only holding temporary for initialization
    private String sdkKey;

    public void postInit() {
        sdkKey = null; // for security, not holding key in memory for long-term
    }
}
