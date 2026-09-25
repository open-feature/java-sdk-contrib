package dev.openfeature.contrib.providers.gofeatureflag.wasm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dylibso.chicory.runtime.ExportFunction;
import dev.openfeature.contrib.providers.gofeatureflag.bean.GoFeatureFlagResponse;
import dev.openfeature.contrib.providers.gofeatureflag.util.Const;
import dev.openfeature.contrib.providers.gofeatureflag.wasm.bean.FlagContext;
import dev.openfeature.contrib.providers.gofeatureflag.wasm.bean.WasmInput;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.Reason;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
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

    /**
     * An input the bundled binary traps on. The guards of 10.1 turn over-large inputs into a
     * PARSE_ERROR, but a query that is malformed rather than over-large still faults the guest, which
     * is why GOFF-WASM-013 asks for trap handling whichever binary ships.
     */
    @SneakyThrows
    private static WasmInput trappingInput() {
        return WasmInput.builder()
                .flagKey("TEST")
                .flag(Const.DESERIALIZE_OBJECT_MAPPER.readTree("{\"variations\":{\"on\":true},"
                        + "\"targeting\":[{\"query\":\"((((\",\"variation\":\"on\"}],"
                        + "\"defaultRule\":{\"variation\":\"on\"}}"))
                .evalContext(Const.DESERIALIZE_OBJECT_MAPPER.readValue("{\"targetingKey\":\"k\"}", java.util.Map.class))
                .flagContext(FlagContext.builder().defaultSdkValue(false).build())
                .build();
    }

    @SneakyThrows
    @DisplayName("a real trap should poison the instance")
    @Test
    void aRealTrapShouldPoisonTheInstance() {
        val instance = new EvaluationWasm();
        instance.preWarmWasm();

        val got = instance.evaluate(trappingInput());

        assertEquals(ErrorCode.GENERAL.name(), got.getErrorCode());
        assertEquals("Trapped on unreachable instruction", got.getErrorDetails());
        assertTrue(instance.isPoisoned(), "a guest fault left the instance usable");
    }

    /**
     * Counts the calls an instance makes to the guest's free, by swapping the export for one that
     * delegates. The requirement is about the call being made at all, and nothing the instance
     * returns reveals whether it was.
     */
    @SneakyThrows
    private static AtomicInteger countFreeCalls(final EvaluationWasm instance) {
        val calls = new AtomicInteger();
        val field = EvaluationWasm.class.getDeclaredField("free");
        field.setAccessible(true);
        val real = (ExportFunction) field.get(instance);
        field.set(instance, (ExportFunction) args -> {
            calls.incrementAndGet();
            return real.apply(args);
        });
        return calls;
    }

    @SneakyThrows
    @DisplayName("a real trap should not have the input buffer freed on it")
    @Test
    void aRealTrapShouldNotHaveTheInputBufferFreedOnIt() {
        val instance = new EvaluationWasm();
        instance.preWarmWasm();
        val freeCalls = countFreeCalls(instance);

        instance.evaluate(trappingInput());

        assertEquals(0, freeCalls.get(), "free was called on a trapped instance");
    }

    @SneakyThrows
    @DisplayName("an evaluation that did not trap should have its input buffer freed")
    @Test
    void anEvaluationThatDidNotTrapShouldHaveItsInputBufferFreed() {
        val instance = new EvaluationWasm();
        instance.preWarmWasm();
        val freeCalls = countFreeCalls(instance);

        instance.evaluate(input().value);

        // the guard is on the trap, not on freeing in general
        assertEquals(1, freeCalls.get());
    }

    @SneakyThrows
    @DisplayName("a real trap should leave the pool able to serve the next evaluation")
    @Test
    void aRealTrapShouldLeaveThePoolAbleToServeTheNextEvaluation() {
        try (val pool = new WasmEvaluatorPool(1)) {
            val trapped = pool.evaluate(trappingInput());
            val next = pool.evaluate(input().value);

            assertEquals(ErrorCode.GENERAL.name(), trapped.getErrorCode());
            // the poisoned instance was discarded and rebuilt rather than handed out again
            assertEquals(true, next.getValue());
            // the engine reports no error as an empty string, not as null
            assertEquals("", next.getErrorCode());
        }
    }

    @SneakyThrows
    @DisplayName("closing the pool should release every instance it holds")
    @Test
    void closingThePoolShouldReleaseEveryInstanceItHolds() {
        val first = instanceThatIsPoisonedAfterEvaluating(false);
        val second = instanceThatIsPoisonedAfterEvaluating(false);
        val pool = new WasmEvaluatorPool(2, new RecordingFactory(List.of(first, second)));

        pool.close();

        verify(first).close();
        verify(second).close();
    }

    @SneakyThrows
    @DisplayName("a discarded instance should be released rather than left holding its descriptors")
    @Test
    void aDiscardedInstanceShouldBeReleasedRatherThanLeftHoldingItsDescriptors() {
        val trapped = instanceThatIsPoisonedAfterEvaluating(true);
        val replacement = instanceThatIsPoisonedAfterEvaluating(false);
        val pool = new WasmEvaluatorPool(1, new RecordingFactory(List.of(trapped, replacement)));

        pool.evaluate(input().value);

        verify(trapped).close();
        verify(replacement, never()).close();
    }

    @SneakyThrows
    @DisplayName("a closed pool should answer with an error instead of blocking on an empty queue")
    @Test
    void aClosedPoolShouldAnswerWithAnErrorInsteadOfBlockingOnAnEmptyQueue() {
        val instance = instanceThatIsPoisonedAfterEvaluating(false);
        val pool = new WasmEvaluatorPool(1, new RecordingFactory(List.of(instance)));
        pool.close();

        val got = pool.evaluate(input().value);

        assertEquals(ErrorCode.GENERAL.name(), got.getErrorCode());
        assertEquals(Reason.ERROR.name(), got.getReason());
    }
}
