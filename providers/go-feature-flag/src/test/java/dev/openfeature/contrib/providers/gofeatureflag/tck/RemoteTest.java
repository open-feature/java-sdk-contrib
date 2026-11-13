package dev.openfeature.contrib.providers.gofeatureflag.tck;

import dev.openfeature.contrib.providers.gofeatureflag.bean.EvaluationType;
import dev.openfeature.contrib.tools.tck.Capability;
import java.util.Set;

/**
 * Runs the OpenFeature Provider TCK against the GO Feature Flag provider in remote mode.
 *
 * <p>Remote evaluation is the OFREP provider underneath ({@code RemoteEvaluator} wraps
 * {@code OfrepProvider}), so its declarations follow the OFREP adoption's, and for the same reasons.
 */
public class RemoteTest extends AbstractGoFeatureFlagTest {

    @Override
    public String configuration() {
        return "go-feature-flag-remote";
    }

    @Override
    protected EvaluationType evaluationType() {
        return EvaluationType.REMOTE;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Withheld, each because this mode does not attempt it, and each measured as failing when
     * declared:
     *
     * <ul>
     *   <li><b>{@link Capability#STALE}</b> and <b>{@link Capability#CONFIGURATION_CHANGE}</b> — the
     *       only outbound call is the per-evaluation OFREP request. Nothing polls, so a change or an
     *       outage is never observed as an event.
     *   <li><b>{@link Capability#UNAVAILABLE_INIT}</b> — {@code RemoteEvaluator.initialize} only
     *       initialises the OFREP provider, which never contacts the backend, so a provider pointed
     *       at a dead port reaches {@code READY}.
     *   <li><b>{@link Capability#NUMERIC_COERCION}</b> — OFREP carries no type, and the OFREP
     *       provider admits a value only on an exact instance check, so {@code integer-flag} asked
     *       for as a float returns the code default. Strict numeric typing is a legitimate choice,
     *       so no deviation. The in-process mode of this same provider does widen.
     *   <li><b>{@link Capability#DISABLED_FLAGS}</b> — the relay proxy answers a disabled flag with
     *       reason {@code DISABLED} and no value, OFREP's {@code codeDefaultFlag}, and the OFREP
     *       provider reports that as {@code FLAG_NOT_FOUND}, dropping the reason
     *       (java-sdk-contrib#1850). The value is the code default either way. Declare it again once
     *       that is fixed; the in-process mode of this same provider reports {@code DISABLED}.
     * </ul>
     */
    @Override
    public Set<Capability> capabilities() {
        return Capability.declarableExcept(
                Capability.STALE,
                Capability.CONFIGURATION_CHANGE,
                Capability.UNAVAILABLE_INIT,
                Capability.NUMERIC_COERCION,
                Capability.DISABLED_FLAGS);
    }
}
