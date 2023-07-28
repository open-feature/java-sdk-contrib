package dev.openfeature.contrib.providers.gofeatureflag.bean;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Bean utils.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class BeanUtils {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static String buildKey(GoFeatureFlagUser goFeatureFlagUser) throws JsonProcessingException {
        return objectMapper.writeValueAsString(goFeatureFlagUser);
    }
}
