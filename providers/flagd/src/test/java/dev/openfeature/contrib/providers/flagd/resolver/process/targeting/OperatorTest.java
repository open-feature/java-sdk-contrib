package dev.openfeature.contrib.providers.flagd.resolver.process.targeting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.time.Instant;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.Value;

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

        long timestamp = (long)Double.parseDouble(timestampString.toString());

        // generating current unix timestamp for testing
        long currentTimestamp = Instant.now().getEpochSecond();
        long thresholdPast = currentTimestamp - (5);
        long thresholdFuture = currentTimestamp + (5);

        // checks if the timestamp is within 5 seconds of the current time (test tolerance)
        assertTrue(timestamp >= thresholdPast && timestamp <= thresholdFuture);
    }

    @Test
    void fractionalTestA() throws TargetingRuleException {
        // given

        // fractional rule with email as expression key
        final String targetingRule = "" +
                "{\n" +
                "  \"fractional\": [\n" +
                "    {\"var\": \"email\"},\n" +
                "    [\n" +
                "      \"red\",\n" +
                "      25\n" +
                "    ],\n" +
                "    [\n" +
                "      \"blue\",\n" +
                "      25\n" +
                "    ],\n" +
                "    [\n" +
                "      \"green\",\n" +
                "      25\n" +
                "    ],\n" +
                "    [\n" +
                "      \"yellow\",\n" +
                "      25\n" +
                "    ]\n" +
                "  ]\n" +
                "}";

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
        final String targetingRule = "" +
                "{\n" +
                "  \"fractional\": [\n" +
                "    {\"var\": \"email\"},\n" +
                "    [\n" +
                "      \"red\",\n" +
                "      25\n" +
                "    ],\n" +
                "    [\n" +
                "      \"blue\",\n" +
                "      25\n" +
                "    ],\n" +
                "    [\n" +
                "      \"green\",\n" +
                "      25\n" +
                "    ],\n" +
                "    [\n" +
                "      \"yellow\",\n" +
                "      25\n" +
                "    ]\n" +
                "  ]\n" +
                "}";

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
        final String targetingRule = "" +
                "{\n" +
                "  \"fractional\": [\n" +
                "    {\"var\": \"email\"},\n" +
                "    [\n" +
                "      \"red\",\n" +
                "      25\n" +
                "    ],\n" +
                "    [\n" +
                "      \"blue\",\n" +
                "      25\n" +
                "    ],\n" +
                "    [\n" +
                "      \"green\",\n" +
                "      25\n" +
                "    ],\n" +
                "    [\n" +
                "      \"yellow\",\n" +
                "      25\n" +
                "    ]\n" +
                "  ]\n" +
                "}";

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
        final String targetingRule = "" +
                "{\n" +
                "  \"starts_with\": [\n" +
                "    {\"var\": \"email\"},\n" +
                "    \"admin\"\n" +
                "  ]\n" +
                "}";

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
        final String targetingRule = "" +
                "{\n" +
                "  \"ends_with\": [\n" +
                "    {\"var\": \"email\"},\n" +
                "    \"@faas.com\"\n" +
                "  ]\n" +
                "}";

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
        final String targetingRule = "{\n" +
                "  \"if\": [\n" +
                "    {\n" +
                "      \"sem_ver\": [{\"var\": \"version\"}, \">=\", \"1.0.0\"]\n" +
                "    },\n" +
                "    \"red\", null\n" +
                "  ]\n" +
                "}";

        Map<String, Value> ctxData = new HashMap<>();
        ctxData.put("version", new Value("1.1.0"));

        // when
        Object evalVariant = OPERATOR.apply("versionFlag", targetingRule, new ImmutableContext(ctxData));

        // then
        assertEquals("red", evalVariant);
    }

}