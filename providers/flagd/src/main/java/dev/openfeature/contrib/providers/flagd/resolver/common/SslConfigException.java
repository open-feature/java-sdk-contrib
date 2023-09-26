package dev.openfeature.contrib.providers.flagd.resolver.common;

/**
 * Custom exception for invalid SSL configurations.
 */
public class SslConfigException extends RuntimeException {
    public SslConfigException(String message) {
        super(message);
    }
}
