package dev.openfeature.contrib.providers.flagd.resolver.process.storage;

import org.junit.jupiter.api.Test;


class JsonStorageTest {

    @Test
    public void deserializerTest(){

        // todo - as a test resource
        String flagConfig = "{\n" +
                "    \"flags\": {\n" +
                "        \"fibAlgo\": {\n" +
                "          \"variants\": {\n" +
                "            \"recursive\": \"recursive\",\n" +
                "            \"memo\": \"memo\",\n" +
                "            \"loop\": \"loop\",\n" +
                "            \"binet\": \"binet\"\n" +
                "          },\n" +
                "          \"defaultVariant\": \"recursive\",\n" +
                "          \"state\": \"ENABLED\",\n" +
                "          \"targeting\": {\n" +
                "            \"if\": [\n" +
                "              {\n" +
                "                \"$ref\": \"emailWithFaas\"\n" +
                "              }, \"binet\", null\n" +
                "            ]\n" +
                "          }\n" +
                "        }\n" +
                "    },\n" +
                "    \"$evaluators\": {\n" +
                "        \"emailWithFaas\": {\n" +
                "              \"in\": [\"@faas.com\", {\n" +
                "                \"var\": [\"email\"]\n" +
                "              }]\n" +
                "        }\n" +
                "    }\n" +
                "}";

        JsonStorage storage = new JsonStorage();

        storage.setFlags(flagConfig);

    }
}