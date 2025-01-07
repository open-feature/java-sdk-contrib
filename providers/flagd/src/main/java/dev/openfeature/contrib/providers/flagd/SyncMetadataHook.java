package dev.openfeature.contrib.providers.flagd;

import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.Hook;
import dev.openfeature.sdk.HookContext;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

class SyncMetadataHook implements Hook<Object> {

    private Supplier<EvaluationContext> contextSupplier;

    SyncMetadataHook(Supplier<EvaluationContext> contextSupplier) {
        this.contextSupplier = contextSupplier;
    }

    /** Return the context adapted from the sync-metadata provided by the supplier. */
    @Override
    public Optional<EvaluationContext> before(HookContext<Object> ctx, Map<String, Object> hints) {
        return Optional.ofNullable(contextSupplier.get());
    }
}
