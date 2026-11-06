package dev.openfeature.contrib.providers.gofeatureflag.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.openfeature.sdk.ImmutableMetadata;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MetadataUtilTest {

    @Test
    void testConvertFlagMetadata_NullInput() {
        // Test when the input is null
        ImmutableMetadata metadata = MetadataUtil.convertFlagMetadata(null);
        assertNotNull(metadata);
    }

    @Test
    void testConvertFlagMetadata_EmptyInput() {
        // Test when the input is an empty map
        Map<String, Object> flagMetadata = new HashMap<>();
        ImmutableMetadata metadata = MetadataUtil.convertFlagMetadata(flagMetadata);
        assertNotNull(metadata);
    }

    @Test
    void testConvertFlagMetadata_WithVariousTypes() {
        // Test with a map containing various types of values
        Map<String, Object> flagMetadata = new HashMap<>();
        flagMetadata.put("key1", 123L); // Long
        flagMetadata.put("key2", 42); // Integer
        flagMetadata.put("key3", 3.14f); // Float
        flagMetadata.put("key4", 2.718); // Double
        flagMetadata.put("key5", true); // Boolean
        flagMetadata.put("key6", "stringValue"); // String

        ImmutableMetadata metadata = MetadataUtil.convertFlagMetadata(flagMetadata);

        assertNotNull(metadata);
        assertEquals(123L, metadata.getLong("key1"));
        assertEquals(42, metadata.getInteger("key2"));
        assertEquals(3.14f, metadata.getFloat("key3"));
        assertEquals(2.718, metadata.getDouble("key4"));
        assertEquals(true, metadata.getBoolean("key5"));
        assertEquals("stringValue", metadata.getString("key6"));
    }

    @Test
    void testConvertFlagMetadata_StructuredValueStaysParseable() {
        Map<String, Object> flagMetadata = new HashMap<>();
        flagMetadata.put("nested", Map.of("a", 1));
        flagMetadata.put("list", List.of(1, 2, 3));

        ImmutableMetadata metadata = MetadataUtil.convertFlagMetadata(flagMetadata);
        assertEquals("{\"a\":1}", metadata.getString("nested"));
        assertEquals("[1,2,3]", metadata.getString("list"));
    }

    @Test
    void testConvertFlagMetadata_NullValueIsSkipped() {
        Map<String, Object> flagMetadata = new HashMap<>();
        flagMetadata.put("present", "value");
        flagMetadata.put("absent", null);

        ImmutableMetadata metadata = MetadataUtil.convertFlagMetadata(flagMetadata);

        assertEquals("value", metadata.getString("present"));
        assertNull(metadata.getString("absent"));
    }
}
