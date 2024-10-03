package dev.openfeature.contrib.providers.flagd.resolver.process;

import dev.openfeature.contrib.providers.flagd.resolver.process.model.FlagParser;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

public class TestUtils {
    public static final String VALID_SIMPLE = "flagConfigurations/valid-simple.json";
    public static final String VALID_SIMPLE_EXTRA_FIELD = "flagConfigurations/valid-simple-with-extra-fields.json";
    public static final String VALID_LONG = "flagConfigurations/valid-long.json";
    public static final String INVALID_FLAG = "flagConfigurations/invalid-flag.json";
    public static final String INVALID_CFG = "flagConfigurations/invalid-configuration.json";
    public static final String UPDATABLE_FILE = "flagConfigurations/updatableFlags.json";

    public static String getFlagsFromResource(final String file) throws IOException {
        final Path resourcePath = getResourcePathInternal(file);
        return new String(Files.readAllBytes(resourcePath));
    }

    public static String getResourcePath(final String relativePath) {
        return getResourcePathInternal(relativePath).toString();
    }

    private static Path getResourcePathInternal(String file) {
        try {
            URL url = Objects.requireNonNull(FlagParser.class.getClassLoader().getResource(file));
            return Paths.get(url.toURI());
        } catch (NullPointerException e) {
            throw new IllegalStateException(String.format("Resource %s not found", file), e);
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Invalid resource path", e);
        }
    }
}
