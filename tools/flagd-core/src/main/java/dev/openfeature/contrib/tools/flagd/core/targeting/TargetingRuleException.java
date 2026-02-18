package dev.openfeature.contrib.tools.flagd.core.targeting;

/**
 * Exception used by targeting rule package.
 */
public class TargetingRuleException extends Exception {

    /**
     * Construct exception.
     *
     * @param message the exception message
     * @param t       the cause
     */
    public TargetingRuleException(final String message, final Throwable t) {
        super(message, t);
    }
}
