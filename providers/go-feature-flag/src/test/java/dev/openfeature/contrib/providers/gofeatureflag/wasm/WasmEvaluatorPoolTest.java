package dev.openfeature.contrib.providers.gofeatureflag.wasm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.openfeature.contrib.providers.gofeatureflag.bean.GoFeatureFlagResponse;
import dev.openfeature.contrib.providers.gofeatureflag.util.Const;
import dev.openfeature.contrib.providers.gofeatureflag.wasm.bean.FlagContext;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WasmEvaluatorPoolTest {

    private static WasmInputFixture input() {
        return new WasmInputFixture();
    }

    private static final class WasmInputFixture {
        private final dev.openfeature.contrib.providers.gofeatureflag.wasm.bean.WasmInput value;

        @SneakyThrows
        private WasmInputFixture() {
            this.value = dev.openfeature.contrib.providers.gofeatureflag.wasm.bean.WasmInput.builder()
                    .flagKey("TEST")
                    .flag(Const.DESERIALIZE_OBJECT_MAPPER.readTree(
                            "{\"variations\":{\"on\":true},\"defaultRule\":{\"variation\":\"on\"}}"))
                    .flagContext(FlagContext.builder().defaultSdkValue(false).build())
                    .build();
        }
    }

    /** a factory handing out prepared instances, recording every one it is asked for. */
    private static final class RecordingFactory implements Supplier<EvaluationWasm> {
        private final List<EvaluationWasm> handedOut = new ArrayList<>();
        private final List<EvaluationWasm> queue;

        private RecordingFactory(List<EvaluationWasm> queue) {
            this.queue = new ArrayList<>(queue);
        }

        @Override
        public EvaluationWasm get() {
            val instance = queue.remove(0);
            handedOut.add(instance);
            return instance;
        }
    }

    private static EvaluationWasm instanceThatIsPoisonedAfterEvaluating(boolean poisoned) {
        val instance = mock(EvaluationWasm.class);
        when(instance.evaluate(any())).thenReturn(new GoFeatureFlagResponse());
        when(instance.isPoisoned()).thenReturn(poisoned);
        return instance;
    }

    @SneakyThrows
    @DisplayName("a trapped instance should be discarded and rebuilt, never served again")
    @Test
    void aTrappedInstanceShouldBeDiscardedAndRebuiltNeverServedAgain() {
        val trapped = instanceThatIsPoisonedAfterEvaluating(true);
        val replacement = instanceThatIsPoisonedAfterEvaluating(false);
        val factory = new RecordingFactory(List.of(trapped, replacement));

        val pool = new WasmEvaluatorPool(1, factory);
        pool.evaluate(input().value);

        assertEquals(List.of(trapped, replacement), factory.handedOut, "a replacement should have been built");

        // the very next evaluation must not land on the poisoned instance
        pool.evaluate(input().value);
        assertNotSame(trapped, factory.handedOut.get(1));
    }

    @SneakyThrows
    @DisplayName("a healthy instance should be returned to the pool unchanged")
    @Test
    void aHealthyInstanceShouldBeReturnedToThePoolUnchanged() {
        val healthy = instanceThatIsPoisonedAfterEvaluating(false);
        val factory = new RecordingFactory(List.of(healthy));

        val pool = new WasmEvaluatorPool(1, factory);
        pool.evaluate(input().value);
        pool.evaluate(input().value);

        assertEquals(1, factory.handedOut.size(), "no instance should have been rebuilt");
        assertSame(healthy, factory.handedOut.get(0));
    }

    @SneakyThrows
    @DisplayName("a real instance should not be poisoned by a successful evaluation")
    @Test
    void aRealInstanceShouldNotBePoisonedByASuccessfulEvaluation() {
        val instance = new EvaluationWasm();
        instance.preWarmWasm();

        val got = instance.evaluate(input().value);

        assertEquals(true, got.getValue());
        assertFalse(instance.isPoisoned());
    }

    @SneakyThrows
    @DisplayName("a guarded input should answer PARSE_ERROR without poisoning the instance")
    @Test
    void aGuardedInputShouldAnswerParseErrorWithoutPoisoningTheInstance() {
        val instance = new EvaluationWasm();
        instance.preWarmWasm();

        val deep = new StringBuilder();
        for (int i = 0; i < 400; i++) {
            deep.append("{\"a\":");
        }
        deep.append("1");
        for (int i = 0; i < 400; i++) {
            deep.append("}");
        }
        val evalContext = Const.DESERIALIZE_OBJECT_MAPPER.readValue(
                "{\"targetingKey\":\"k\",\"deep\":" + deep + "}", java.util.Map.class);

        val wasmInput = dev.openfeature.contrib.providers.gofeatureflag.wasm.bean.WasmInput.builder()
                .flagKey("TEST")
                .flag(Const.DESERIALIZE_OBJECT_MAPPER.readTree(
                        "{\"variations\":{\"on\":true},\"defaultRule\":{\"variation\":\"on\"}}"))
                .evalContext(evalContext)
                .flagContext(FlagContext.builder().defaultSdkValue(false).build())
                .build();

        val got = instance.evaluate(wasmInput);

        assertEquals("PARSE_ERROR", got.getErrorCode());
        assertFalse(instance.isPoisoned(), "a guarded input is not a guest fault, the instance is still healthy");
    }
}
