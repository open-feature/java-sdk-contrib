package dev.openfeature.contrib.providers.flagd.resolver.process.model;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FlagParserTest {

    private static final String VALID_SIMPLE = "flagConfigurations/valid-simple.json";
    private static final String VALID_LONG = "flagConfigurations/valid-long.json";
    private static final String INVALID_FLAG = "flagConfigurations/invalid-flag.json";
    private static final String INVALID_CFG = "flagConfigurations/invalid-configuration.json";

    @Test
    public void validJsonConfigurationParsing() throws IOException {
        Map<String, FeatureFlag> flagMap = FlagParser.parseString(getFlagsFromResource(VALID_SIMPLE));
        FeatureFlag boolFlag = flagMap.get("myBoolFlag");

        assertNotNull(boolFlag);
        assertEquals("ENABLED", boolFlag.getState());
        assertEquals("on", boolFlag.getDefaultVariant());

        Map<String, Object> variants = boolFlag.getVariants();

        assertEquals(true, variants.get("on"));
        assertEquals(false, variants.get("off"));
    }

    @Test
    public void validJsonConfigurationWithTargetingRulesParsing() throws IOException {
        Map<String, FeatureFlag> flagMap = FlagParser.parseString(getFlagsFromResource(VALID_LONG));
        FeatureFlag stringFlag = flagMap.get("fibAlgo");

        assertNotNull(stringFlag);
        assertEquals("ENABLED", stringFlag.getState());
        assertEquals("recursive", stringFlag.getDefaultVariant());

        Map<String, Object> variants = stringFlag.getVariants();

        assertEquals("recursive", variants.get("recursive"));
        assertEquals("memo", variants.get("memo"));
        assertEquals("loop", variants.get("loop"));
        assertEquals("binet", variants.get("binet"));

        assertEquals("{\"if\":[{\"in\":[\"@faas.com\",{\"var\":[\"email\"]}]},\"binet\",null]}", stringFlag.getTargeting());
    }


    @Test
    public void invalidFlagThrowsError() {
        assertThrows(IllegalArgumentException.class, () -> {
            FlagParser.parseString(getFlagsFromResource(INVALID_FLAG));
        });
    }

    @Test
    public void invalidConfigurationsThrowsError() {
        assertThrows(IllegalArgumentException.class, () -> {
            FlagParser.parseString(getFlagsFromResource(INVALID_CFG));
        });
    }

    private static String getFlagsFromResource(final String file) throws IOException {
        final URL url = FlagParser.class.getClassLoader().getResource(file);
        if (url == null) {
            throw new IllegalStateException(String.format("Resource %s not found", file));
        } else {
            return new String(Files.readAllBytes(Paths.get(url.getPath())));
        }
    }
}