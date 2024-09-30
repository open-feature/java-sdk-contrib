package dev.openfeature.contrib.providers.flagd;

import static org.junit.Assert.assertEquals;

import java.util.Optional;
import java.util.function.Supplier;

import org.junit.Test;

import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.FlagValueType;
import dev.openfeature.sdk.HookContext;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.MutableContext;

/**
 * SyncMetadataHookTest
 */
public class SyncMetadataHookTest {

    @Test
    public void shouldCallContextSupplierAndReturnContext() {
        MutableContext suppliedContext = new MutableContext();
        String key1 = "key1";
        String val1 = "val1";
        Supplier<EvaluationContext> contextSupplier = () -> suppliedContext;

        suppliedContext.add(key1, val1);

        // when(contextSupplier.get()).thenReturn(new ImmutableContext("some-key"));
        SyncMetadataHook hook = new SyncMetadataHook(contextSupplier);
        Optional<EvaluationContext> context = hook.before(HookContext.builder().flagKey("some-flag").defaultValue(false)
                .type(FlagValueType.BOOLEAN).ctx(new ImmutableContext()).build(), null);
        assertEquals(val1, context.get().getValue(key1).asString());
    }
}