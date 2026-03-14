package dev.openfeature.contrib.tools.flagd.api.testkit;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Utility for loading the testkit's bundled flag configuration from the classpath.
 *
 * <p>The flag configuration is copied from the {@code open-feature/test-harness}
 * submodule ({@code evaluator/flags/testkit-flags.json}) into the JAR at build time.
 * Consumers call {@link #loadFlags()} to retrieve the JSON string and pass it to
 * {@link dev.openfeature.contrib.tools.flagd.api.Evaluator#setFlags(String)}.
 */
public final class TestkitFlags {

    static final String TESTKIT_FLAGS_RESOURCE = "flags/testkit-flags.json";

    private TestkitFlags() {}

    /**
     * Loads the bundled flag configuration JSON from the classpath.
     *
     * @return the flag configuration as a JSON string
     * @throws IOException if the resource cannot be found or read
     */
    public static String loadFlags() throws IOException {
        try (InputStream is = TestkitFlags.class.getClassLoader().getResourceAsStream(TESTKIT_FLAGS_RESOURCE)) {
            if (is == null) {
                throw new IOException("Testkit flag resource not found on classpath: " + TESTKIT_FLAGS_RESOURCE
                        + ". Ensure the flagd-api-testkit JAR was built with 'mvn generate-resources'.");
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
