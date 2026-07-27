package dev.openfeature.contrib.tools.providertck;

import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.MutableContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Scenario-scoped mutable state, injected into every step definition class by PicoContainer.
 *
 * <p>One instance per scenario. Anything that must survive across scenarios — the Compose stack,
 * the control API client, the discovered harness — lives in {@link TckRuntime} instead.
 */
@SuppressFBWarnings(
        value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD",
        justification = "Intentional mutable state sharing required by Cucumber PicoContainer DI")
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
     * <p>Used by the configuration-change scenario. Asserting "the value changed" rather than "the
     * value is now X" keeps the scenario portable: the control API only requires that
     * {@code POST /change} changes the resolved value of {@code changing-flag}, not which concrete
     * value it changes to.
     */
    public Object rememberedValue;

    /**
     * Any exception thrown out of the most recent evaluation call.
     *
     * <p>The SDK contract is that typed evaluation never throws — errors surface as an error code
     * and the code default. The evaluation step records rather than propagates, so a scenario can
     * assert this explicitly instead of a thrown exception merely showing up as a step failure.
     */
    public RuntimeException evaluationException;

    /** Events observed by handlers registered in this scenario. */
    public final ConcurrentLinkedQueue<ProviderEventRecord> events = new ConcurrentLinkedQueue<>();

    /** The event most recently matched by an await step. */
    public Optional<ProviderEventRecord> lastEvent = Optional.empty();
}
