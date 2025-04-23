package dev.openfeature.contrib.providers.v2.gofeatureflag.exception;

import dev.openfeature.contrib.providers.gofeatureflag.exception.GoFeatureFlagException;
import lombok.experimental.StandardException;

/** InvalidEndpoint is thrown when we don't have any endpoint in the configuration. */
@StandardException
public class FlagConfigurationEndpointNotFound extends GoFeatureFlagException {}
