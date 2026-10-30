package dev.openfeature.contrib.providers.gofeatureflag.tck;

import dev.openfeature.contrib.providers.gofeatureflag.bean.EvaluationType;
import dev.openfeature.contrib.tools.tck.Capability;
import dev.openfeature.contrib.tools.tck.KnownDeviation;
import java.util.Collections;
import java.util.List;
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
     * </ul>
     *
     * <p>{@link Capability#DISABLED_FLAGS} is declared and fails, see {@link #knownDeviations()}.
     */
    @Override
    public Set<Capability> capabilities() {
        return Capability.declarableExcept(
                Capability.STALE,
                Capability.CONFIGURATION_CHANGE,
                Capability.UNAVAILABLE_INIT,
                Capability.NUMERIC_COERCION);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Declared and failing, the shape Appendix F prefers: the relay proxy answers a disabled flag
     * with reason {@code DISABLED} and no value, OFREP's {@code codeDefaultFlag}, and the OFREP
     * provider reports that as {@code FLAG_NOT_FOUND}. All five disabled-flag scenarios fail on the
     * error code or reason, never on the value. Delete with the declaration's failure once
     * java-sdk-contrib#1850 is fixed.
     */
    @Override
    public List<KnownDeviation> knownDeviations() {
        return Collections.singletonList(KnownDeviation.tracked(
                Capability.DISABLED_FLAGS,
                "https://github.com/open-feature/java-sdk-contrib/issues/1850",
                "A value-less OFREP evaluation success is reported as FLAG_NOT_FOUND. The relay "
                        + "proxy answers a disabled flag with reason DISABLED and no value, which is "
                        + "OFREP's codeDefaultFlag; the OFREP provider this mode delegates to treats "
                        + "the missing value as an absent flag and drops the reason. The value "
                        + "returned is the code default, as it should be; the error code and reason "
                        + "are wrong. The in-process mode of the same provider reports DISABLED."));
    }
}
