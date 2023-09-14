package dev.openfeature.contrib.providers.flagd.resolver.process.targeting;

import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.Value;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OperatorTest {
    private static Operator OPERATOR;

    @BeforeAll
    static void setUp() {
        OPERATOR = new Operator();
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