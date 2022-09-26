package dev.openfeature.contrib.providers.gofeatureflag.exception;

import dev.openfeature.javasdk.ErrorCode;
import dev.openfeature.javasdk.exceptions.OpenFeatureError;
import lombok.experimental.StandardException;

/**
 * InvalidTargetingKey is the error send when we don't have a targeting key.
 */
@StandardException
public class InvalidTargetingKey extends OpenFeatureError {
    public ErrorCode getErrorCode() {
        // Should change as soon as we have a better error type.
        return ErrorCode.GENERAL;
    }
}
