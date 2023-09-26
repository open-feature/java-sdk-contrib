package dev.openfeature.contrib.providers.flagd.resolver.process.targeting;

/**
 * Exception used by targeting rule package.
 **/
public class TargetingRuleException extends Exception {

    /**
     * Construct exception.
     **/
    public TargetingRuleException(final String message, final Throwable t) {
        super(message, t);
    }
}
