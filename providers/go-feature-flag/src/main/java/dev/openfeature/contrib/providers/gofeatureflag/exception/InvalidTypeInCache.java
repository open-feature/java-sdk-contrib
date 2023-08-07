package dev.openfeature.contrib.providers.gofeatureflag.exception;

import lombok.experimental.StandardException;

/**
 * InvalidTypeInCache is thrown when the type of the flag from the cache is not the one expected.
 */
public class InvalidTypeInCache extends GoFeatureFlagException {
    public InvalidTypeInCache(Class<?> expected, Class<?> got) {
        super("cache value is not from the expected type, we try a remote evaluation,"
                + " expected: " + expected + ", got: " + got);
    }
}
