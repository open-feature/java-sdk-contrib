package dev.openfeature.contrib.providers.flagsmith.exceptions;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import dev.openfeature.sdk.ErrorCode;
import lombok.Generated;
import lombok.experimental.StandardException;

/**
 * InvalidOptions is the super Exception used when we have a configuration exception.
 */
@StandardException
public class InvalidOptions extends FlagsmithProviderException {
    private static final long serialVersionUID = 1L;
    private final ErrorCode errorCode;

    @SuppressFBWarnings(
        justification = "generated code"
    )
    @Generated
    public InvalidOptions() {
        this((String) null, (Throwable) null);
    }

    @SuppressFBWarnings(
        justification = "generated code"
    )
    @Generated
    public InvalidOptions(String message) {
        this(message, (Throwable) null);
    }

    @SuppressFBWarnings(
        justification = "generated code"
    )
    @Generated
    public InvalidOptions(Throwable cause) {
        this(cause != null ? cause.getMessage() : null, cause);
    }

    @SuppressFBWarnings(
        justification = "generated code"
    )
    @Generated
    public InvalidOptions(String message, Throwable cause) {
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
