package dev.openfeature.contrib.providers.v2.gofeatureflag.validator;

import dev.openfeature.contrib.providers.v2.gofeatureflag.GoFeatureFlagProviderOptions;
import dev.openfeature.contrib.providers.v2.gofeatureflag.exception.InvalidEndpoint;
import dev.openfeature.contrib.providers.v2.gofeatureflag.exception.InvalidOptions;

public class Validator {
    public static void ProviderOptions(GoFeatureFlagProviderOptions options) throws InvalidOptions {
        if (options == null) {
            throw new InvalidOptions("No options provided");
        }

        if (options.getEndpoint() == null || options.getEndpoint().isEmpty()) {
            throw new InvalidEndpoint("endpoint is a mandatory field when initializing the provider");
        }
    }
}
