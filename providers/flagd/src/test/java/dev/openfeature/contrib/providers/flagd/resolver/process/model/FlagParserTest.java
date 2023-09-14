package dev.openfeature.contrib.providers.flagd.resolver.process.model;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static dev.openfeature.contrib.providers.flagd.resolver.process.TestUtils.INVALID_CFG;
import static dev.openfeature.contrib.providers.flagd.resolver.process.TestUtils.INVALID_FLAG;
import static dev.openfeature.contrib.providers.flagd.resolver.process.TestUtils.VALID_LONG;
import static dev.openfeature.contrib.providers.flagd.resolver.process.TestUtils.VALID_SIMPLE;
import static dev.openfeature.contrib.providers.flagd.resolver.process.TestUtils.getFlagsFromResource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FlagParserTest {
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

        assertEquals("{\"if\":[{\"in\":[\"@faas.com\",{\"var\":[\"email\"]}]},\"binet\",null]}",
                stringFlag.getTargeting());
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
}
