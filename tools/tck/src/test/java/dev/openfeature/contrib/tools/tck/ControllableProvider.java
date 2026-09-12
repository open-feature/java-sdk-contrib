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
 * <p>It exists because {@link InMemoryProvider} cannot cover the lifecycle feature and never will:
 * it is handed its whole flag set by its constructor, so {@code initialize()} does nothing but
 * record a state, and {@code shutdown()} releases nothing there is any way to observe. Running the
 * lifecycle scenarios against it would establish nothing, which is why
 * {@link InMemoryProviderTckTest} leaves {@link Capability#LIFECYCLE} undeclared. The consequence
 * was that the {@code @lifecycle} and {@code @reinitialization} steps — shutdown, double shutdown,
 * shutdown against a dead backend, initialise again — had no coverage without Docker, and a
 * regression in them surfaced first inside a containerised provider suite, where it looks like a
 * provider defect.
 *
 * <p>This provider therefore acquires something. It starts owning nothing, {@code initialize()}
 * reaches a {@linkplain Supplier store} that may refuse it, and {@code shutdown()} drops what was
 * acquired. The store is in this JVM rather than over a socket, but the <em>shape</em> is the one
 * the lifecycle scenarios assert: initialisation can fail, its outcome is observable, shutdown
 * releases and can be repeated, and initialising again brings the provider back.
 *
 * <p>Composition rather than {@code extends InMemoryProvider}, deliberately. Seeding a subclass's
 * flags at {@code initialize()} time means calling {@code updateFlags}, which emits
 * {@code PROVIDER_CONFIGURATION_CHANGED} — so initialisation would emit a configuration change
 * every time, and a test double that emits events the thing it stands in for would not emit is
 * worse than no double. Holding the delegate in a field keeps every emission deliberate.
 *
 * <p>Not part of the published API. An adopter with no backend uses
 * {@link InProcessBackendControl} and the SDK's own {@link InMemoryProvider}; this is the TCK
 * testing itself.
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
     * scenario: a provider that returns early because an {@code initialized} flag was never cleared
     * would pass the first and fail the second.
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
     * <p>Idempotent, which is what "shutting down a provider twice has no further effect" asks for:
     * the second call finds {@code null} and returns. {@code super.shutdown()} is deliberately not
     * called — it terminates {@link EventProvider}'s emitter executor, which would make this
     * provider unusable after a shutdown the specification permits it to recover from. The SDK
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
     * <p>The delegate is not registered with the SDK, so an event emitted from it reaches nobody.
     * Mutating the delegate's store and emitting from here is what makes the event the suite awaits
     * arrive on the client the scenario is holding.
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
     * stale values: the whole claim of the shutdown scenarios is that shutdown released something.
     */
    private InMemoryProvider requireDelegate() {
        InMemoryProvider current = delegate;
        if (current == null) {
            throw new GeneralError(NAME + " has no flag store: initialize() has not run, or shutdown() released it.");
        }
        return current;
    }
}
