package dev.openfeature.contrib.providers.gofeatureflag.exception;

import lombok.experimental.StandardException;

/** Thrown when it is impossible to retrieve the flag configuration. */
@StandardException
public class ImpossibleToRetrieveConfiguration extends GoFeatureFlagRuntimeException {}
