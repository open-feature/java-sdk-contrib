package dev.openfeature.contrib.providers.gofeatureflag.bean;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Bean utils.
 */
public class BeanUtils {

    private static ObjectMapper objectMapper = new ObjectMapper();

    private BeanUtils() {

    }

    public static String buildKey(GoFeatureFlagUser goFeatureFlagUser) throws JsonProcessingException {
        return objectMapper.writeValueAsString(goFeatureFlagUser);
    }
}
