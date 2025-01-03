package dev.openfeature.contrib.providers.envvar;

/**
 * This internal class abstracts away Java's {@link System} class for test purposes. It is not
 * intended to be used directly.
 */
class OS implements EnvironmentGateway {
    public String getEnvironmentVariable(String key) {
        return System.getenv(key);
    }
}
