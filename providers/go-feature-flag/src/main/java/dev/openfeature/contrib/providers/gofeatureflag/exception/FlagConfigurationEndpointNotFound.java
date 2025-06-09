package dev.openfeature.contrib.providers.gofeatureflag.exception;

import lombok.experimental.StandardException;

/** Thrown when it is impossible to find the flag configuration endpoint. */
@StandardException
public class FlagConfigurationEndpointNotFound extends GoFeatureFlagRuntimeException {}
