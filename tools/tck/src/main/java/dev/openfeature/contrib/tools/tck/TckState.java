package dev.openfeature.contrib.tools.tck;

import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.MutableContext;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Scenario-scoped mutable state, injected into step definitions by PicoContainer. Suite-wide state
 * lives in {@link TckRuntime}.
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

    /** A previously resolved value, so a later evaluation can be asserted to differ. */
    public Object rememberedValue;

    /** Exception from the most recent provider call, recorded rather than propagated. */
    public Exception thrown;

    /** The call {@link #thrown} came from; {@code null} when none. */
    public String thrownBy;

    /** Duration of the most recent {@code shutdown()} call, or {@code null} if none. */
    public Duration shutdownDuration;

    /** Events observed by handlers registered in this scenario. */
    public final ConcurrentLinkedQueue<ProviderEventRecord> events = new ConcurrentLinkedQueue<>();

    /** The event most recently matched by an await step. */
    public Optional<ProviderEventRecord> lastEvent = Optional.empty();
}
