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
 * An in-JVM provider with a <strong>real initialisation</strong>, for the TCK's own Docker-free
 * self-test.
 *
 * <p>Unlike {@link InMemoryProvider}, which is handed its whole flag set by its constructor, this
 * provider acquires something: it starts owning nothing, {@code initialize()} reaches a
 * {@linkplain Supplier store} that may refuse it, and {@code shutdown()} drops what was acquired.
 * The store is in this JVM rather than over a socket, but the <em>shape</em> is the one the
 * lifecycle scenarios assert — see {@link ControllableProviderTckTest} for why that coverage is
 * needed without Docker.
 *
 * <p><strong>Composition rather than {@code extends InMemoryProvider}, deliberately.</strong>
 * Seeding a subclass's flags at {@code initialize()} time means calling {@code updateFlags}, which
 * emits {@code PROVIDER_CONFIGURATION_CHANGED}, so every initialisation would fire a spurious
 * configuration change at the very scenarios that assert which events occur. Holding the delegate in
 * a field keeps every emission deliberate.
 *
 * <p>Not part of the published API. An adopter with no backend uses
 * {@link InProcessBackendControl} and the SDK's own {@link InMemoryProvider}.
 */
final class ControllableProvider extends EventProvider {

    /** What this provider calls itself. Asserted only as "not empty", by the metadata feature. */
    private static final String NAME = "TckControllableProvider";

    /**
     * The backend initialisation reaches. Returns the flag set to serve, or throws when the backend
     * is unreachable — which is how the {@code @unavailable} scenarios get a provider that fails to
     * initialise without needing a closed socket.
     */
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

    /**
     * Acquires the flag set from the backend, and fails if the backend will not give it up.
     *
     * <p>Called by the SDK on registration, and directly by the {@code the provider is initialized
     * again} step. Both paths are the same code, which is the point of the reinitialisation
     * scenario.
     *
     * @param context the scenario's evaluation context
     * @throws Exception if the backend is unreachable
     */
    @Override
    public void initialize(EvaluationContext context) throws Exception {
        InMemoryProvider acquired = new InMemoryProvider(backend.get());
        acquired.initialize(context);
        delegate = acquired;
    }

    /**
     * Releases the flag set.
     *
     * <p>Idempotent: the second call finds {@code null} and returns. {@code super.shutdown()} is
     * deliberately not called — it terminates {@link EventProvider}'s emitter executor, which would
     * make this provider unusable after a shutdown it is permitted to recover from. The SDK
     * terminates that executor when the provider is replaced, which the TCK does after every
     * scenario.
     */
    @Override
    public void shutdown() {
        delegate = null;
    }

    @Override
    public ProviderState getState() {
        InMemoryProvider current = delegate;
        return current == null ? ProviderState.NOT_READY : current.getState();
    }

    /**
     * Changes a flag and says so, from this provider rather than from the delegate.
     *
     * <p>The delegate is not registered with the SDK, so an event emitted from it reaches nobody;
     * emitting from here is what makes the awaited event arrive on the scenario's client.
     *
     * @param key the flag that changed
     * @param flag its new definition
     */
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

    /**
     * The store, or a failure that names the cause.
     *
     * <p>An evaluation reaching a shut-down provider is a real error rather than a reason to serve
     * stale values, since the claim of the shutdown scenarios is that shutdown released something.
     */
    private InMemoryProvider requireDelegate() {
        InMemoryProvider current = delegate;
        if (current == null) {
            throw new GeneralError(NAME + " has no flag store: initialize() has not run, or shutdown() released it.");
        }
        return current;
    }
}
