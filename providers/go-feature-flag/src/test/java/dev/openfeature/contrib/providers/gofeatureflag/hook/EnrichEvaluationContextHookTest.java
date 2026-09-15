package dev.openfeature.contrib.providers.gofeatureflag.hook;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.openfeature.contrib.providers.gofeatureflag.TestUtils;
import dev.openfeature.sdk.FlagValueType;
import dev.openfeature.sdk.HookContext;
import dev.openfeature.sdk.MutableContext;
import dev.openfeature.sdk.MutableStructure;
import dev.openfeature.sdk.Value;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
        val hookContext = HookContext.<String>from(
                "testFlagKey", FlagValueType.BOOLEAN, null, null, TestUtils.defaultEvaluationContext, "default");
        val res = hook.before(hookContext, null);
        assertEquals(Optional.of(hookContext.getCtx()), res);
    }

    @DisplayName("Should return the same context if no metadata provided")
    @SneakyThrows
    @Test
    void shouldReturnSameContextIfMetadataEmpty() {
        EnrichEvaluationContextHook hook = new EnrichEvaluationContextHook(Collections.emptyMap());
        val hookContext = HookContext.<String>from(
                "testFlagKey", FlagValueType.BOOLEAN, null, null, TestUtils.defaultEvaluationContext, "default");
        val res = hook.before(hookContext, null);
        assertEquals(Optional.of(hookContext.getCtx()), res);
    }

    @DisplayName("Should preserve the caller keys of the gofeatureflag namespace")
    @SneakyThrows
    @Test
    void shouldPreserveTheCallerKeysOfTheGoFeatureFlagNamespace() {
        EnrichEvaluationContextHook hook = new EnrichEvaluationContextHook(Map.of("appVersion", "1.2.3"));
        val callerNamespace = new MutableStructure()
                .add("flagList", List.of(new Value("flag-a"), new Value("flag-b")))
                .add("currentDateTime", "2026-09-15T00:00:00Z");
        val callerContext = new MutableContext("user-key").add("gofeatureflag", callerNamespace);
        val hookContext =
                HookContext.<String>from("testFlagKey", FlagValueType.BOOLEAN, null, null, callerContext, "default");

        val got = hook.before(hookContext, null).get().getValue("gofeatureflag").asStructure();

        assertEquals(
                List.of(new Value("flag-a"), new Value("flag-b")),
                got.getValue("flagList").asList());
        assertEquals("2026-09-15T00:00:00Z", got.getValue("currentDateTime").asString());
        assertEquals(
                "1.2.3",
                got.getValue("exporterMetadata")
                        .asStructure()
                        .getValue("appVersion")
                        .asString());
    }

    @DisplayName("Should replace a caller supplied exporterMetadata")
    @SneakyThrows
    @Test
    void shouldReplaceACallerSuppliedExporterMetadata() {
        EnrichEvaluationContextHook hook = new EnrichEvaluationContextHook(Map.of("appVersion", "1.2.3"));
        val callerNamespace = new MutableStructure()
                .add("exporterMetadata", new MutableStructure().add("appVersion", "from-the-caller"));
        val callerContext = new MutableContext("user-key").add("gofeatureflag", callerNamespace);
        val hookContext =
                HookContext.<String>from("testFlagKey", FlagValueType.BOOLEAN, null, null, callerContext, "default");

        val got = hook.before(hookContext, null).get().getValue("gofeatureflag").asStructure();

        assertEquals(
                "1.2.3",
                got.getValue("exporterMetadata")
                        .asStructure()
                        .getValue("appVersion")
                        .asString());
    }

    @DisplayName("Should replace the gofeatureflag namespace if it is not a structure")
    @SneakyThrows
    @Test
    void shouldReplaceTheGoFeatureFlagNamespaceIfItIsNotAStructure() {
        EnrichEvaluationContextHook hook = new EnrichEvaluationContextHook(Map.of("appVersion", "1.2.3"));
        val callerContext = new MutableContext("user-key").add("gofeatureflag", "not-a-structure");
        val hookContext =
                HookContext.<String>from("testFlagKey", FlagValueType.BOOLEAN, null, null, callerContext, "default");

        val got = hook.before(hookContext, null).get().getValue("gofeatureflag").asStructure();

        assertEquals(
                "1.2.3",
                got.getValue("exporterMetadata")
                        .asStructure()
                        .getValue("appVersion")
                        .asString());
    }
}
