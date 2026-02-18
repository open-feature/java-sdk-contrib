package dev.openfeature.contrib.tools.flagd.api;

/**
 * Exception thrown when flag store operations fail.
 */
public class FlagStoreException extends Exception {

    /**
     * Construct a FlagStoreException with a message.
     *
     * @param message the exception message
     */
    public FlagStoreException(String message) {
        super(message);
    }

    /**
     * Construct a FlagStoreException with a message and cause.
     *
     * @param message the exception message
     * @param cause   the underlying cause
     */
    public FlagStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
