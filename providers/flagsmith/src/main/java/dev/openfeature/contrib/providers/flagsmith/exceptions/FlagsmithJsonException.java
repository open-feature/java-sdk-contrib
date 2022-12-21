package dev.openfeature.contrib.providers.flagsmith.exceptions;

import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.exceptions.GeneralError;
import lombok.Getter;

/**
 * FlagsmithJsonException is the super Exception used when the json returned by
 * Flagsmith can't be mapped to OpenFeature Value objects.
 */
@Getter
public class FlagsmithJsonException extends GeneralError {
    private static final long serialVersionUID = 1L;
    private final ErrorCode errorCode = ErrorCode.GENERAL;

    public FlagsmithJsonException(String message) {
        super(message);
    }

}
