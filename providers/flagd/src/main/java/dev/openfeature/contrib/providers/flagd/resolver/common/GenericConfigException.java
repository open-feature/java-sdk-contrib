package dev.openfeature.contrib.providers.flagd.resolver.common;

/**
 * Custom exception for invalid gRPC configurations.
 */

public class GenericConfigException extends RuntimeException {
    public GenericConfigException(String message) {
        super(message);
    }
}
