package dev.openfeature.contrib.providers.gofeatureflag.api.bean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openfeature.contrib.providers.gofeatureflag.bean.GoFeatureFlagResponse;
import java.util.HashMap;
import java.util.Map;
import lombok.val;
import org.junit.jupiter.api.Test;

class OfrepResponseTest {
    @Test
    void testToGoFeatureFlagResponse_AllFieldsMappedCorrectly() {
        val ofrepResponse = new OfrepResponse();
        ofrepResponse.setValue("testValue");
        ofrepResponse.setKey("testKey");
        ofrepResponse.setVariant("testVariant");
        ofrepResponse.setReason("testReason");
        ofrepResponse.setErrorCode("testErrorCode");
        ofrepResponse.setErrorDetails("testErrorDetails");

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("gofeatureflag_cacheable", true);
        metadata.put("gofeatureflag_version", "v1.0");
        metadata.put("extra_metadata", "extraValue");
        ofrepResponse.setMetadata(metadata);

        // Act
        GoFeatureFlagResponse goFeatureFlagResponse = ofrepResponse.toGoFeatureFlagResponse();

        // Assert
        assertEquals("testValue", goFeatureFlagResponse.getValue());
        assertEquals("testVariant", goFeatureFlagResponse.getVariationType());
        assertEquals("testReason", goFeatureFlagResponse.getReason());
        assertEquals("testErrorCode", goFeatureFlagResponse.getErrorCode());
        assertEquals("testErrorDetails", goFeatureFlagResponse.getErrorDetails());
        assertTrue(goFeatureFlagResponse.isFailed());
        assertTrue(goFeatureFlagResponse.isCacheable());
        assertEquals("v1.0", goFeatureFlagResponse.getVersion());
        assertEquals(1, goFeatureFlagResponse.getMetadata().size());
        assertEquals("extraValue", goFeatureFlagResponse.getMetadata().get("extra_metadata"));
    }

    @Test
    void testToGoFeatureFlagResponse_NoCacheableOrVersionInMetadata() {
        OfrepResponse ofrepResponse = new OfrepResponse();
        ofrepResponse.setMetadata(new HashMap<>());

        GoFeatureFlagResponse goFeatureFlagResponse = ofrepResponse.toGoFeatureFlagResponse();

        assertFalse(goFeatureFlagResponse.isCacheable());
        assertNull(goFeatureFlagResponse.getVersion());
    }

    @Test
    void testToGoFeatureFlagResponse_NullMetadata() {
        // Arrange
        OfrepResponse ofrepResponse = new OfrepResponse();
        ofrepResponse.setMetadata(null);

        // Act
        GoFeatureFlagResponse goFeatureFlagResponse = ofrepResponse.toGoFeatureFlagResponse();

        // Assert
        assertNull(goFeatureFlagResponse.getMetadata());
    }

    @Test
    void testToGoFeatureFlagResponse_ErrorCodeIsNull() {
        OfrepResponse ofrepResponse = new OfrepResponse();
        ofrepResponse.setErrorCode(null);
        GoFeatureFlagResponse goFeatureFlagResponse = ofrepResponse.toGoFeatureFlagResponse();
        assertFalse(goFeatureFlagResponse.isFailed());
    }
}
