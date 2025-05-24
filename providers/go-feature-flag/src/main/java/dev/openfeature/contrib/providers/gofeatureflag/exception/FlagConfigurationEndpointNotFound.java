package dev.openfeature.contrib.providers.gofeatureflag.exception;

import lombok.experimental.StandardException;

/** InvalidEndpoint is thrown when we don't have any endpoint in the configuration. */
@StandardException
public class FlagConfigurationEndpointNotFound extends GoFeatureFlagRuntimeException {}
