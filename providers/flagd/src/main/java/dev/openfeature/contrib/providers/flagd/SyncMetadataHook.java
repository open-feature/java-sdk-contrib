package dev.openfeature.contrib.providers.flagd;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.Hook;
import dev.openfeature.sdk.HookContext;

class SyncMetadataHook implements Hook<Object> {

    private Supplier<EvaluationContext> contextSupplier;

    SyncMetadataHook(Supplier<EvaluationContext> contextSupplier) {
        this.contextSupplier = contextSupplier;
    }

    /**
     * Return the enriched context, including the additional attributes from the sync-metadata.
     */
    @Override
    public Optional<EvaluationContext> before(HookContext<Object> ctx, Map<String, Object> hints) {
        return Optional.ofNullable(contextSupplier.get());
    }
}