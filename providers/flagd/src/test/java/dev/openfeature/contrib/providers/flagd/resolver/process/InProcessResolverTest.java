package dev.openfeature.contrib.providers.flagd.resolver.process;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import io.github.jamsesso.jsonlogic.JsonLogic;
import io.github.jamsesso.jsonlogic.JsonLogicException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

class InProcessResolverTest {

    @Test
    public void testInitializer() throws Exception {
        FlagdOptions options = FlagdOptions.builder()
                .host("localhost")
                .port(8080)
                .build();

        InProcessResolver resolver = new InProcessResolver(options);

        resolver.init();

        // delay for init
        Thread.sleep(2000);

        resolver.booleanEvaluation("booleanFlag", false, null);

        Thread.sleep(100_000);
    }

    @Test
    public void jsonEvaluator() throws JsonProcessingException {
        String logic  = "{\"if\":[{\"in\":[\"@faas.com\",{\"var\":[\"email\"]}]},\"binet\",null]}";

        ObjectMapper mapper = new ObjectMapper();

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("email", "abc@faas.com");

        JsonLogic jsonLogic = new JsonLogic();

        try {
            Object result = jsonLogic.apply(logic, ctx);

            System.out.println(result);
        } catch (JsonLogicException e) {
            throw new RuntimeException(e);
        }
    }

}