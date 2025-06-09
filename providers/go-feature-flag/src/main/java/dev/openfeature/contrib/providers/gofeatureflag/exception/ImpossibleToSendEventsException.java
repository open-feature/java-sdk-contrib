package dev.openfeature.contrib.providers.gofeatureflag.exception;

import lombok.experimental.StandardException;

/**
 * This exception is thrown when the SDK is unable to send events to the GO Feature Flag server.
 */
@StandardException
public class ImpossibleToSendEventsException extends GoFeatureFlagRuntimeException {}
