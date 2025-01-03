package dev.openfeature.contrib.providers.flagd.resolver.process.model;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static dev.openfeature.contrib.providers.flagd.resolver.process.TestUtils.INVALID_CFG;
import static dev.openfeature.contrib.providers.flagd.resolver.process.TestUtils.INVALID_FLAG;
import static dev.openfeature.contrib.providers.flagd.resolver.process.TestUtils.INVALID_FLAG_METADATA;
import static dev.openfeature.contrib.providers.flagd.resolver.process.TestUtils.VALID_LONG;
import static dev.openfeature.contrib.providers.flagd.resolver.process.TestUtils.VALID_SIMPLE;
import static dev.openfeature.contrib.providers.flagd.resolver.process.TestUtils.VALID_SIMPLE_EXTRA_FIELD;
import static dev.openfeature.contrib.providers.flagd.resolver.process.TestUtils.getFlagsFromResource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FlagParserTest {
    @Test
    void validJsonConfigurationParsing() throws IOException {
        Map<String, FeatureFlag> flagMap = FlagParser.parseString(getFlagsFromResource(VALID_SIMPLE), true);
        FeatureFlag boolFlag = flagMap.get("myBoolFlag");

        assertNotNull(boolFlag);
        assertEquals("ENABLED", boolFlag.getState());
        assertEquals("on", boolFlag.getDefaultVariant());

        Map<String, Object> variants = boolFlag.getVariants();

        assertEquals(true, variants.get("on"));
        assertEquals(false, variants.get("off"));

        Map<String, Object> metadata = boolFlag.getMetadata();

        assertInstanceOf(String.class, metadata.get("string"));
        assertEquals("string", metadata.get("string"));

        assertInstanceOf(Boolean.class, metadata.get("boolean"));
        assertEquals(true, metadata.get("boolean"));

        assertInstanceOf(Double.class, metadata.get("float"));
        assertEquals(1.234, metadata.get("float"));
    }

    @Test
    void validJsonConfigurationWithExtraFieldsParsing() throws IOException {
        Map<String, FeatureFlag> flagMap = FlagParser.parseString(getFlagsFromResource(VALID_SIMPLE_EXTRA_FIELD), true);
        FeatureFlag boolFlag = flagMap.get("myBoolFlag");

        assertNotNull(boolFlag);
        assertEquals("ENABLED", boolFlag.getState());
        assertEquals("on", boolFlag.getDefaultVariant());

        Map<String, Object> variants = boolFlag.getVariants();

        assertEquals(true, variants.get("on"));
        assertEquals(false, variants.get("off"));
    }

    @Test
    void validJsonConfigurationWithTargetingRulesParsing() throws IOException {
        Map<String, FeatureFlag> flagMap = FlagParser.parseString(getFlagsFromResource(VALID_LONG), true);
        FeatureFlag stringFlag = flagMap.get("fibAlgo");

        assertNotNull(stringFlag);
        assertEquals("ENABLED", stringFlag.getState());
        assertEquals("recursive", stringFlag.getDefaultVariant());

        Map<String, Object> variants = stringFlag.getVariants();

        assertEquals("recursive", variants.get("recursive"));
        assertEquals("memo", variants.get("memo"));
        assertEquals("loop", variants.get("loop"));
        assertEquals("binet", variants.get("binet"));

        assertEquals("{\"if\":[{\"in\":[\"@faas.com\",{\"var\":[\"email\"]}]},\"binet\",null]}",
                stringFlag.getTargeting());
    }


    @Test
    void invalidFlagThrowsError() throws IOException {
        String flagString = getFlagsFromResource(INVALID_FLAG);
        assertThrows(IllegalArgumentException.class, () -> {
            FlagParser.parseString(flagString, true);
        });
    }

    @Test
    void invalidFlagMetadataThrowsError() throws IOException {
        String flagString = getFlagsFromResource(INVALID_FLAG_METADATA);
        assertThrows(IllegalArgumentException.class, () -> {
            FlagParser.parseString(flagString, true);
        });
    }

    @Test
    void invalidConfigurationsThrowsError() throws IOException {
        String flagString = getFlagsFromResource(INVALID_CFG);
        assertThrows(IllegalArgumentException.class, () -> {
            FlagParser.parseString(flagString, true);
        });
    }
}
