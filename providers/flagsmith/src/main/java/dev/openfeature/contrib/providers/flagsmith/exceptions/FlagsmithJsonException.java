package dev.openfeature.contrib.providers.flagsmith.exceptions;

import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.exceptions.GeneralError;
import lombok.Getter;

/**
 * A Flagsmith provider exception is the main exception for the provider.
 */
@Getter
public class FlagsmithJsonException extends GeneralError {
    private static final long serialVersionUID = 1L;
    private final ErrorCode errorCode = ErrorCode.GENERAL;

    public FlagsmithJsonException(String message) {
        super(message);
    }

}
