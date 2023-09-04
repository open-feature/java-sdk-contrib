package dev.openfeature.contrib.providers.flagd.resolver.process.storage;

import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.resolver.process.model.FeatureFlag;
import dev.openfeature.contrib.providers.flagd.resolver.process.model.FlagParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;


class JsonStorageTest {

    @Test
    public void deserializerTest() throws IOException {

        // todo - as a test resource
        String flagConfig = "{\n" +
                "   \"flags\":{\n" +
                "      \"fibAlgo\":{\n" +
                "         \"variants\":{\n" +
                "            \"recursive\":\"recursive\",\n" +
                "            \"memo\":\"memo\",\n" +
                "            \"loop\":\"loop\",\n" +
                "            \"binet\":\"binet\"\n" +
                "         },\n" +
                "         \"defaultVariant\":\"recursive\",\n" +
                "         \"state\":\"ENABLED\",\n" +
                "         \"targeting\":{\n" +
                "            \"if\":[\n" +
                "               {\n" +
                "                  \"$ref\":\"emailWithFaas\"\n" +
                "               },\n" +
                "               \"binet\",\n" +
                "               null\n" +
                "            ]\n" +
                "         }\n" +
                "      }\n" +
                "   },\n" +
                "   \"$evaluators\":{\n" +
                "      \"emailWithFaas\":{\n" +
                "         \"in\":[\n" +
                "            \"@faas.com\",\n" +
                "            {\n" +
                "               \"var\":[\n" +
                "                  \"email\"\n" +
                "               ]\n" +
                "            }\n" +
                "         ]\n" +
                "      }\n" +
                "   }\n" +
                "}";

        String expectedTargeting = "{\"if\":[{\"in\":[\"@faas.com\",{\"var\":[\"email\"]}]},\"binet\",null]}";

        Map<String, FeatureFlag> flagMap = FlagParser.parseString(flagConfig);

        assertEquals(expectedTargeting, flagMap.get("fibAlgo").getTargeting());
    }
}