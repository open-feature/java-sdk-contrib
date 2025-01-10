package dev.openfeature.contrib.providers.flagd.resolver.process.model;

import static dev.openfeature.contrib.providers.flagd.resolver.process.TestUtils.INVALID_CFG;
import static dev.openfeature.contrib.providers.flagd.resolver.process.TestUtils.INVALID_FLAG;
import static dev.openfeature.contrib.providers.flagd.resolver.process.TestUtils.INVALID_FLAG_METADATA;
import static dev.openfeature.contrib.providers.flagd.resolver.process.TestUtils.INVALID_GLOBAL_FLAG_METADATA;
import static dev.openfeature.contrib.providers.flagd.resolver.process.TestUtils.VALID_GLOBAL_FLAG_METADATA;
import static dev.openfeature.contrib.providers.flagd.resolver.process.TestUtils.VALID_LONG;
import static dev.openfeature.contrib.providers.flagd.resolver.process.TestUtils.VALID_SIMPLE;
import static dev.openfeature.contrib.providers.flagd.resolver.process.TestUtils.VALID_SIMPLE_EXTRA_FIELD;
import static dev.openfeature.contrib.providers.flagd.resolver.process.TestUtils.getFlagsFromResource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FlagParserTest {
    @Test
    void validJsonConfigurationParsing() throws IOException {
        Map<String, FeatureFlag> flagMap =
                FlagParser.parseString(getFlagsFromResource(VALID_SIMPLE), true).getFlags();
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

        assertNotNull(boolFlag.getMetadata());
        assertEquals(3, boolFlag.getMetadata().size());
        assertEquals("string", boolFlag.getMetadata().get("string"));
        assertEquals(true, boolFlag.getMetadata().get("boolean"));
        assertEquals(1.234, boolFlag.getMetadata().get("float"));
    }

    @Test
    void validJsonConfigurationWithExtraFieldsParsing() throws IOException {
        Map<String, FeatureFlag> flagMap = FlagParser.parseString(getFlagsFromResource(VALID_SIMPLE_EXTRA_FIELD), true)
                .getFlags();
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
        Map<String, FeatureFlag> flagMap =
                FlagParser.parseString(getFlagsFromResource(VALID_LONG), true).getFlags();
        FeatureFlag stringFlag = flagMap.get("fibAlgo");

        assertNotNull(stringFlag);
        assertEquals("ENABLED", stringFlag.getState());
        assertEquals("recursive", stringFlag.getDefaultVariant());

        Map<String, Object> variants = stringFlag.getVariants();

        assertEquals("recursive", variants.get("recursive"));
        assertEquals("memo", variants.get("memo"));
        assertEquals("loop", variants.get("loop"));
        assertEquals("binet", variants.get("binet"));

        assertEquals(
                "{\"if\":[{\"in\":[\"@faas.com\",{\"var\":[\"email\"]}]},\"binet\",null]}", stringFlag.getTargeting());
    }

    @Test
    void validJsonConfigurationWithGlobalMetadataParsing() throws IOException {
        ParsingResult parsingResult = FlagParser.parseString(getFlagsFromResource(VALID_GLOBAL_FLAG_METADATA), true);
        Map<String, FeatureFlag> flagMap = parsingResult.getFlags();
        FeatureFlag flag = flagMap.get("without-metadata");

        assertNotNull(flag);

        Map<String, Object> metadata = flag.getMetadata();
        Map<String, Object> globalMetadata = parsingResult.getGlobalFlagMetadata();

        assertNotNull(metadata);
        assertNull(metadata.get("string"));
        assertNull(metadata.get("boolean"));
        assertNull(metadata.get("float"));
        assertNotNull(globalMetadata);
        assertEquals("some string", globalMetadata.get("string"));
        assertEquals(true, globalMetadata.get("boolean"));
        assertEquals(1.234, globalMetadata.get("float"));
    }

    @Test
    void validJsonConfigurationWithFlagMetadataParsing() throws IOException {
        ParsingResult parsingResult = FlagParser.parseString(getFlagsFromResource(VALID_GLOBAL_FLAG_METADATA), true);
        Map<String, FeatureFlag> flagMap = parsingResult.getFlags();
        FeatureFlag flag = flagMap.get("with-metadata");

        assertNotNull(flag);

        Map<String, Object> metadata = flag.getMetadata();
        Map<String, Object> globalMetadata = parsingResult.getGlobalFlagMetadata();

        assertNotNull(globalMetadata);
        assertEquals("some string", globalMetadata.get("string"));
        assertEquals(true, globalMetadata.get("boolean"));
        assertEquals(1.234, globalMetadata.get("float"));
        assertNotNull(metadata);
        assertEquals("other string", metadata.get("string"));
        assertEquals(true, metadata.get("boolean"));
        assertEquals(2.71828, metadata.get("float"));
    }

    @Test
    void invalidFlagThrowsError() throws IOException {
        String flagString = getFlagsFromResource(INVALID_FLAG);
        assertThrows(IllegalArgumentException.class, () -> FlagParser.parseString(flagString, true));
    }

    @Test
    void invalidFlagMetadataThrowsError() throws IOException {
        String flagString = getFlagsFromResource(INVALID_FLAG_METADATA);
        assertThrows(IllegalArgumentException.class, () -> FlagParser.parseString(flagString, true));
    }

    @Test
    void invalidGlobalFlagMetadataThrowsError() throws IOException {
        String flagString = getFlagsFromResource(INVALID_GLOBAL_FLAG_METADATA);
        assertThrows(IllegalArgumentException.class, () -> FlagParser.parseString(flagString, true));
    }

    @Test
    void invalidConfigurationsThrowsError() throws IOException {
        String flagString = getFlagsFromResource(INVALID_CFG);
        assertThrows(IllegalArgumentException.class, () -> FlagParser.parseString(flagString, true));
    }
}
