package dev.openfeature.contrib.tools.flagd.core.targeting;

import static dev.openfeature.contrib.tools.flagd.core.targeting.Operator.TARGET_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.Value;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OperatorTest {
    private static Operator OPERATOR;

    @BeforeAll
    static void setUp() {
        OPERATOR = new Operator();
    }

    @Test
    void flagKeyPresent() throws TargetingRuleException {
        // given

        // rule asserting $flagd.flagKey equals the flag key
        final String targetingRule = "{\"===\":[{\"var\":[\"$flagd.flagKey\"]},\"some-key\"]}";

        // when
        Object evalVariant = OPERATOR.apply("some-key", targetingRule, new ImmutableContext());

        // then
        assertEquals(true, evalVariant);
    }

    @Test
    void timestampPresent() throws TargetingRuleException {
        // given

        // rule asserting $flagd.timestamp is a number (i.e., a Unix timestamp)
        final String targetingRule = "{\"var\":[\"$flagd.timestamp\"]}";

        // when
        Object timestampString = OPERATOR.apply("some-key", targetingRule, new ImmutableContext());
        long timestamp = (long) Double.parseDouble(timestampString.toString());

        // generating current unix timestamp & 5 minute threshold
        long currentTimestamp = Instant.now().getEpochSecond();
        long thresholdPast = currentTimestamp - (5);
        long thresholdFuture = currentTimestamp + (5);

        // checks if the timestamp is within 5 minutes of the current time
        assertTrue(timestamp >= thresholdPast && timestamp <= thresholdFuture);
    }

    @Test
    void testFlagPropertiesConstructor() {
        // Given
        Map<String, Object> flagdProperties = new HashMap<>();
        flagdProperties.put(Operator.FLAG_KEY, "some-key");
        flagdProperties.put(Operator.TIME_STAMP, 1634000000L);

        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put(TARGET_KEY, "myTargetingKey");
        dataMap.put(Operator.FLAGD_PROPS_KEY, flagdProperties);

        // When
        Operator.FlagProperties flagProperties = new Operator.FlagProperties(dataMap);

        // Then
        assertEquals("some-key", flagProperties.getFlagKey());
        assertEquals("myTargetingKey", flagProperties.getTargetingKey());
        assertEquals(1634000000L, flagProperties.getTimestamp());
    }

    @Test
    void fractionalTestA() throws TargetingRuleException {
        // given

        // fractional rule with email as expression key
        final String targetingRule = ""
                + "{\n"
                + "  \"fractional\": [\n"
                + "    {\"cat\":[\n"
                + "      {\"var\":\"$flagd.flagKey\"},\n"
                + "      {\"var\": \"email\"}\n"
                + "    ]},\n"
                + "    [\n"
                + "      \"red\",\n"
                + "      25\n"
                + "    ],\n"
                + "    [\n"
                + "      \"blue\",\n"
                + "      25\n"
                + "    ],\n"
                + "    [\n"
                + "      \"green\",\n"
                + "      25\n"
                + "    ],\n"
                + "    [\n"
                + "      \"yellow\",\n"
                + "      25\n"
                + "    ]\n"
                + "  ]\n"
                + "}";

        Map<String, Value> ctxData = new HashMap<>();
        ctxData.put("email", new Value("rachel@faas.com"));

        // when
        Object evalVariant = OPERATOR.apply("headerColor", targetingRule, new ImmutableContext(ctxData));

        // then
        assertEquals("yellow", evalVariant);
    }

    @Test
    void fractionalTestB() throws TargetingRuleException {
        // given

        // fractional rule with email as expression key
        final String targetingRule = ""
                + "{\n"
                + "  \"fractional\": [\n"
                + "    {\"cat\":[\n"
                + "      {\"var\":\"$flagd.flagKey\"},\n"
                + "      {\"var\": \"email\"}\n"
                + "    ]},\n"
                + "    [\n"
                + "      \"red\",\n"
                + "      25\n"
                + "    ],\n"
                + "    [\n"
                + "      \"blue\",\n"
                + "      25\n"
                + "    ],\n"
                + "    [\n"
                + "      \"green\",\n"
                + "      25\n"
                + "    ],\n"
                + "    [\n"
                + "      \"yellow\",\n"
                + "      25\n"
                + "    ]\n"
                + "  ]\n"
                + "}";

        Map<String, Value> ctxData = new HashMap<>();
        ctxData.put("email", new Value("monica@faas.com"));

        // when
        Object evalVariant = OPERATOR.apply("headerColor", targetingRule, new ImmutableContext(ctxData));

        // then
        assertEquals("blue", evalVariant);
    }

    @Test
    void fractionalTestC() throws TargetingRuleException {
        // given

        // fractional rule with email as expression key
        final String targetingRule = ""
                + "{\n"
                + "  \"fractional\": [\n"
                + "    {\"cat\":[\n"
                + "      {\"var\":\"$flagd.flagKey\"},\n"
                + "      {\"var\": \"email\"}\n"
                + "    ]},\n"
                + "    [\n"
                + "      \"red\",\n"
                + "      25\n"
                + "    ],\n"
                + "    [\n"
                + "      \"blue\",\n"
                + "      25\n"
                + "    ],\n"
                + "    [\n"
                + "      \"green\",\n"
                + "      25\n"
                + "    ],\n"
                + "    [\n"
                + "      \"yellow\",\n"
                + "      25\n"
                + "    ]\n"
                + "  ]\n"
                + "}";

        Map<String, Value> ctxData = new HashMap<>();
        ctxData.put("email", new Value("joey@faas.com"));

        // when
        Object evalVariant = OPERATOR.apply("headerColor", targetingRule, new ImmutableContext(ctxData));

        // then
        assertEquals("red", evalVariant);
    }

    @Test
    void stringCompStartsWith() throws TargetingRuleException {
        // given

        // starts with rule with email as expression key
        final String targetingRule = ""
                + "{\n"
                + "  \"starts_with\": [\n"
                + "    {\"var\": \"email\"},\n"
                + "    \"admin\"\n"
                + "  ]\n"
                + "}";

        Map<String, Value> ctxData = new HashMap<>();
        ctxData.put("email", new Value("admin@faas.com"));

        // when
        Object evalVariant = OPERATOR.apply("adminRule", targetingRule, new ImmutableContext(ctxData));

        // then
        assertEquals(true, evalVariant);
    }

    @Test
    void stringCompEndsWith() throws TargetingRuleException {
        // given

        // ends with rule with email as expression key
        final String targetingRule = ""
                + "{\n"
                + "  \"ends_with\": [\n"
                + "    {\"var\": \"email\"},\n"
                + "    \"@faas.com\"\n"
                + "  ]\n"
                + "}";

        Map<String, Value> ctxData = new HashMap<>();
        ctxData.put("email", new Value("admin@faas.com"));

        // when
        Object evalVariant = OPERATOR.apply("isFaas", targetingRule, new ImmutableContext(ctxData));

        // then
        assertEquals(true, evalVariant);
    }

    @Test
    void semVerA() throws TargetingRuleException {
        // given

        // sem_ver rule with version as expression key
        final String targetingRule = "{\n"
                + "  \"if\": [\n"
                + "    {\n"
                + "      \"sem_ver\": [{\"var\": \"version\"}, \">=\", \"1.0.0\"]\n"
                + "    },\n"
                + "    \"red\", null\n"
                + "  ]\n"
                + "}";

        Map<String, Value> ctxData = new HashMap<>();
        ctxData.put("version", new Value("1.1.0"));

        // when
        Object evalVariant = OPERATOR.apply("versionFlag", targetingRule, new ImmutableContext(ctxData));

        // then
        assertEquals("red", evalVariant);
    }
}
