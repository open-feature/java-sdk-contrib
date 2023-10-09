package dev.openfeature.contrib.providers.flagd.resolver.process;

import dev.openfeature.contrib.providers.flagd.resolver.process.model.FlagParser;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;

public class TestUtils {
    public static final String VALID_SIMPLE = "flagConfigurations/valid-simple.json";
    public static final String VALID_SIMPLE_EXTRA_FIELD = "flagConfigurations/valid-simple-with-extra-fields.json";
    public static final String VALID_LONG = "flagConfigurations/valid-long.json";
    public static final String INVALID_FLAG = "flagConfigurations/invalid-flag.json";
    public static final String INVALID_CFG = "flagConfigurations/invalid-configuration.json";


    public static String getFlagsFromResource(final String file) throws IOException {
        final URL url = FlagParser.class.getClassLoader().getResource(file);
        if (url == null) {
            throw new IllegalStateException(String.format("Resource %s not found", file));
        } else {
            return new String(Files.readAllBytes(Paths.get(url.getPath())));
        }
    }

    public static String getResourcePath(final String relativePath) {
        final URL url = FlagParser.class.getClassLoader().getResource(relativePath);
        if (url == null) {
            throw new IllegalStateException(String.format("Resource %s not found", relativePath));
        }
        return url.getPath();
    }
}
