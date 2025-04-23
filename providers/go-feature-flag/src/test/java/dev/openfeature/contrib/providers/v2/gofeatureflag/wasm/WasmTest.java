package dev.openfeature.contrib.providers.v2.gofeatureflag.wasm;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.openfeature.contrib.providers.v2.gofeatureflag.GoFeatureFlagProvider;
import dev.openfeature.contrib.providers.v2.gofeatureflag.GoFeatureFlagProviderOptions;
import dev.openfeature.contrib.providers.v2.gofeatureflag.bean.EvaluationType;
import dev.openfeature.contrib.providers.v2.gofeatureflag.wasm.bean.WasmInput;
import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.MutableContext;
import dev.openfeature.sdk.OpenFeatureAPI;
import java.io.InputStream;
import java.util.Date;
import lombok.SneakyThrows;
import lombok.val;
import lombok.var;
import org.junit.Test;

public class WasmTest {
    @Test
    @SneakyThrows
    public void testWasm() {
        val fileLocation = "wasm_inputs/valid.json";
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(fileLocation);
        if (inputStream == null) {
            throw new IllegalArgumentException("File not found: " + fileLocation);
        }

        // Use ObjectMapper to parse the JSON into a WasmInput object
        ObjectMapper objectMapper = new ObjectMapper();
        WasmInput wasmInput = objectMapper.readValue(inputStream, WasmInput.class);
        val evalWasm = new EvaluationWasm();
        val output = evalWasm.evaluate(wasmInput);
        System.out.println(output);

    }

    @Test
    @SneakyThrows
    public void XXX() {
        val options = GoFeatureFlagProviderOptions.builder()
                .endpoint("http://localhost:1031/")
                .evaluationType(EvaluationType.IN_PROCESS)
                .flagChangePollingIntervalMs(1000L)
                .build();
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(options);
        OpenFeatureAPI.getInstance().setProviderAndWait("toto", g);
        Client client = OpenFeatureAPI.getInstance().getClient("toto");
        MutableContext evaluationContext = new MutableContext().setTargetingKey("XXX").add("toto", 123);
        var before = new Date();
        val value = client.getBooleanDetails("TEST", false, evaluationContext);
        var after = new Date();
        System.out.println("Time taken: " + (after.getTime() - before.getTime()) + " milliseconds");
        before = new Date();
        client.getBooleanDetails("TEST", false, evaluationContext);
        after = new Date();
        System.out.println("Time taken: " + (after.getTime() - before.getTime()) + " milliseconds");
        Thread.sleep(100000);
        System.out.println(value);
    }
}
