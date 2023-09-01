package dev.openfeature.contrib.providers.flagd.resolver.process.storage;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureFlagTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void deserializerTest() throws JsonProcessingException {
        // given
        final String flag =
                "{\"state\": \"ENABLED\"," +
                        "\"variants\": { \"on\": true, \"off\": false }," +
                        "\"defaultVariant\": \"on\"}";

        // when
        FeatureFlag parsed = MAPPER.readValue(flag, FeatureFlag.class);

        // then
        assertEquals("ENABLED", parsed.getState());
        assertEquals("on", parsed.getDefaultVariant());
        assertEquals("", parsed.getTargeting());

        Map<String, Object> variants = parsed.getVariants();

        assertTrue((Boolean) variants.get("on"));
        assertFalse((Boolean) variants.get("off"));
    }
}