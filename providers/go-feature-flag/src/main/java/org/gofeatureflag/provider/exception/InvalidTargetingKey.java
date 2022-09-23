package org.gofeatureflag.provider.exception;

import dev.openfeature.javasdk.ErrorCode;
import dev.openfeature.javasdk.exceptions.OpenFeatureError;

import javax.management.openmbean.OpenDataException;

public class InvalidTargetingKey extends OpenFeatureError {
    public ErrorCode getErrorCode() {
        // Should change as soon as we have a better error type.
        return ErrorCode.GENERAL;
    }
}
