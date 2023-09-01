package dev.openfeature.contrib.providers.flagd.resolver.process.storage;

import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


class JsonStorageTest {

    @Test
    public void deserializerTest(){

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

        FlagStore storage = new FlagStore(FlagdOptions.builder().build());

        storage.setFlags(flagConfig);

        FeatureFlag fibAlgo = storage.getFLag("fibAlgo");

        assertEquals(expectedTargeting, fibAlgo.getTargeting());
    }
}