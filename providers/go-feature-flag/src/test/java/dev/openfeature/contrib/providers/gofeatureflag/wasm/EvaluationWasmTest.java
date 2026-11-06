package dev.openfeature.contrib.providers.gofeatureflag.wasm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.DeserializationFeature;
import dev.openfeature.contrib.providers.gofeatureflag.TestUtils;
import dev.openfeature.contrib.providers.gofeatureflag.util.Const;
import java.nio.charset.StandardCharsets;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EvaluationWasmTest {

    @SneakyThrows
    @DisplayName("the engine should answer each canonical ABI vector with its canonical output")
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"valid", "missing-targeting-key", "invalid"})
    void theEngineShouldAnswerEachCanonicalAbiVectorWithItsCanonicalOutput(String vector) {
        try (val instance = new EvaluationWasm()) {
            instance.preWarmWasm();

            val output = instance.evaluateRaw(
                    TestUtils.readMockResponse("wasm_inputs/", vector + ".json").getBytes(StandardCharsets.UTF_8));

            assertEquals(
                    Const.DESERIALIZE_OBJECT_MAPPER.readTree(
                            TestUtils.readMockResponse("wasm_outputs/", vector + ".json")),
                    Const.DESERIALIZE_OBJECT_MAPPER
                            .reader()
                            .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                            .readTree(output),
                    "the bundled engine must answer the Appendix B.3 vector exactly as canon does");
        }
    }
}
