package dev.openfeature.contrib.providers.gofeatureflag.hook;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.openfeature.contrib.providers.gofeatureflag.TestUtils;
import dev.openfeature.sdk.FlagValueType;
import dev.openfeature.sdk.HookContext;
import java.util.Collections;
import java.util.Optional;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class EnrichEvaluationContextHookTest {
    @DisplayName("Should return nothing if no options provided")
    @SneakyThrows
    @Test
    void shouldReturnNothingIfNoOptionsProvided() {
        EnrichEvaluationContextHook hook = new EnrichEvaluationContextHook(null);
        val res = hook.before(null, null);
        assertEquals(Optional.empty(), res);
    }

    @DisplayName("Should return the same context if no metadata provided")
    @SneakyThrows
    @Test
    void shouldReturnSameContextIfNoMetadataProvided() {
        EnrichEvaluationContextHook hook = new EnrichEvaluationContextHook(null);
        val hookContext = HookContext.<String>builder()
                .ctx(TestUtils.defaultEvaluationContext)
                .flagKey("testFlagKey")
                .type(FlagValueType.BOOLEAN)
                .defaultValue("default")
                .build();
        val res = hook.before(hookContext, null);
        assertEquals(Optional.of(hookContext.getCtx()), res);
    }

    @DisplayName("Should return the same context if no metadata provided")
    @SneakyThrows
    @Test
    void shouldReturnSameContextIfMetadataEmpty() {
        EnrichEvaluationContextHook hook = new EnrichEvaluationContextHook(Collections.emptyMap());
        val hookContext = HookContext.<String>builder()
                .ctx(TestUtils.defaultEvaluationContext)
                .flagKey("testFlagKey")
                .type(FlagValueType.BOOLEAN)
                .defaultValue("default")
                .build();
        val res = hook.before(hookContext, null);
        assertEquals(Optional.of(hookContext.getCtx()), res);
    }
}
