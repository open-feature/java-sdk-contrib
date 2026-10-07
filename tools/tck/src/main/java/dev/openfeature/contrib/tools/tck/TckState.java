package dev.openfeature.contrib.tools.tck;

import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.MutableContext;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Scenario-scoped mutable state, injected into every step definition class by PicoContainer.
 *
 * <p>One instance per scenario. Anything that must survive across scenarios — the Compose stack,
 * the control API client, the discovered harness — lives in {@link TckRuntime} instead.
 */
public class TckState {

    /** Client bound to the domain the provider under test is registered under. */
    public Client client;

    /** The provider under test. */
    public FeatureProvider provider;

    /** Scenario-scoped OpenFeature domain, so scenarios cannot see each other's providers. */
    public String domain;

    /** The flag the current scenario is exercising. */
    public FlagUnderTest flag;

    /** Evaluation context accumulated by the context steps. */
    public MutableContext context = new MutableContext();

    /** Result of the most recent evaluation. */
    public FlagEvaluationDetails<?> evaluation;

    /**
     * A previously resolved value, captured so a later evaluation can be asserted to differ.
     *
     * <p>Used by the configuration-change scenario, which asserts that the value changed rather
     * than what it became — the control API does not say which value {@code POST /change} picks.
     */
    public Object rememberedValue;

    /**
     * Any exception thrown out of the most recent call the scenario made on the provider, with
     * {@link #thrownBy} naming the call.
     *
     * <p>Evaluation, shutdown and re-initialisation all record here rather than propagate, into one
     * slot asserted by one step — {@code no exception should have been thrown} — so a scenario
     * states the expectation explicitly instead of a throw merely showing up as a step failure.
     */
    public Exception thrown;

    /** The call {@link #thrown} came out of, for the failure message; {@code null} when none did. */
    public String thrownBy;

    /**
     * How long the most recent direct {@code shutdown()} call took, or {@code null} before one was
     * made in this scenario.
     *
     * <p>Recorded so a scenario can assert that shutdown against a backend that will never answer
     * returns promptly instead of blocking on a graceful close.
     */
    public Duration shutdownDuration;

    /** Events observed by handlers registered in this scenario. */
    public final ConcurrentLinkedQueue<ProviderEventRecord> events = new ConcurrentLinkedQueue<>();

    /** The event most recently matched by an await step. */
    public Optional<ProviderEventRecord> lastEvent = Optional.empty();
}
