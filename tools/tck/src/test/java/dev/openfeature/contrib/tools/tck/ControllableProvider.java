package dev.openfeature.contrib.tools.tck;

import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.EventProvider;
import dev.openfeature.sdk.Metadata;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.ProviderEventDetails;
import dev.openfeature.sdk.ProviderState;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.GeneralError;
import dev.openfeature.sdk.providers.memory.Flag;
import dev.openfeature.sdk.providers.memory.InMemoryProvider;
import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;

/**
 * An in-JVM provider with a real initialisation that can fail, for Docker-free lifecycle coverage.
 * Wraps rather than extends {@link InMemoryProvider} to avoid spurious configuration-change events.
 */
final class ControllableProvider extends EventProvider {

    /** What this provider calls itself. Asserted only as "not empty", by the metadata feature. */
    private static final String NAME = "TckControllableProvider";

    /** Supplies the flag set, or throws when the backend is unreachable. */
    private final Supplier<Map<String, Flag<?>>> backend;

    /** The flag store serving the current session, or {@code null} before init and after shutdown. */
    private volatile InMemoryProvider delegate;

    ControllableProvider(Supplier<Map<String, Flag<?>>> backend) {
        this.backend = backend;
    }

    @Override
    public Metadata getMetadata() {
        return () -> NAME;
    }

    /** Acquires the flag set from the backend; throws if it is unreachable. */
    @Override
    public void initialize(EvaluationContext context) throws Exception {
        InMemoryProvider acquired = new InMemoryProvider(backend.get());
        acquired.initialize(context);
        delegate = acquired;
    }

    /** Releases the flag set; skips {@code super.shutdown()} so the provider can be reinitialised. */
    @Override
    public void shutdown() {
        delegate = null;
    }

    @Override
    public ProviderState getState() {
        InMemoryProvider current = delegate;
        return current == null ? ProviderState.NOT_READY : current.getState();
    }

    /** Changes a flag and emits the event from this provider, since the delegate is unregistered. */
    void changeFlag(String key, Flag<?> flag) {
        requireDelegate().updateFlag(key, flag);
        emitProviderConfigurationChanged(ProviderEventDetails.builder()
                .flagsChanged(Collections.singletonList(key))
                .message("flag changed by the TCK's in-process control")
                .build());
    }

    @Override
    public ProviderEvaluation<Boolean> getBooleanEvaluation(String key, Boolean fallback, EvaluationContext ctx) {
        return requireDelegate().getBooleanEvaluation(key, fallback, ctx);
    }

    @Override
    public ProviderEvaluation<String> getStringEvaluation(String key, String fallback, EvaluationContext ctx) {
        return requireDelegate().getStringEvaluation(key, fallback, ctx);
    }

    @Override
    public ProviderEvaluation<Integer> getIntegerEvaluation(String key, Integer fallback, EvaluationContext ctx) {
        return requireDelegate().getIntegerEvaluation(key, fallback, ctx);
    }

    @Override
    public ProviderEvaluation<Double> getDoubleEvaluation(String key, Double fallback, EvaluationContext ctx) {
        return requireDelegate().getDoubleEvaluation(key, fallback, ctx);
    }

    @Override
    public ProviderEvaluation<Value> getObjectEvaluation(String key, Value fallback, EvaluationContext ctx) {
        return requireDelegate().getObjectEvaluation(key, fallback, ctx);
    }

    /** The store, or an error if not initialised or shut down. */
    private InMemoryProvider requireDelegate() {
        InMemoryProvider current = delegate;
        if (current == null) {
            throw new GeneralError(NAME + " has no flag store: initialize() has not run, or shutdown() released it.");
        }
        return current;
    }
}
