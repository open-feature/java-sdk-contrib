package dev.openfeature.contrib.providers.flagd.resolver.process.targeting;

public class TargetingRuleException extends Exception{

    public TargetingRuleException(final String message, final Throwable t){
        super(message, t);
    }
}
