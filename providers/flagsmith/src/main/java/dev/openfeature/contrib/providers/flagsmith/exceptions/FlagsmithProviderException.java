package dev.openfeature.contrib.providers.flagsmith.exceptions;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.exceptions.GeneralError;
import lombok.Generated;

/**
 * A Flagsmith provider exception is the main exception for the provider
 */
public class FlagsmithProviderException extends GeneralError {
    private static final long serialVersionUID = 1L;
    private final ErrorCode errorCode;

    @SuppressFBWarnings(
        justification = "generated code"
    )
    @Generated
    public FlagsmithProviderException() {
        this((String) null, (Throwable) null);
    }

    @SuppressFBWarnings(
        justification = "generated code"
    )
    @Generated
    public FlagsmithProviderException(String message) {
        this(message, (Throwable) null);
    }

    @SuppressFBWarnings(
        justification = "generated code"
    )
    @Generated
    public FlagsmithProviderException(Throwable cause) {
        this(cause != null ? cause.getMessage() : null, cause);
    }

    @SuppressFBWarnings(
        justification = "generated code"
    )
    @Generated
    public FlagsmithProviderException(String message, Throwable cause) {
        super(message);
        this.errorCode = ErrorCode.GENERAL;
        if (cause != null) {
            super.initCause(cause);
        }

    }

    @SuppressFBWarnings(
        justification = "generated code"
    )
    @Generated
    public ErrorCode getErrorCode() {
        return this.errorCode;
    }
}
