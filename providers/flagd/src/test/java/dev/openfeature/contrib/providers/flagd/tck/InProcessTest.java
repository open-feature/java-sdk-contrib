package dev.openfeature.contrib.providers.flagd.tck;

import dev.openfeature.contrib.providers.flagd.Config;
import dev.openfeature.contrib.tools.tck.Capability;
import dev.openfeature.contrib.tools.tck.KnownDeviation;
import java.util.Collections;
import java.util.List;

/** Runs the OpenFeature Provider TCK against the flagd provider in in-process mode. */
public class InProcessTest extends AbstractResolverTest {

    /**
     * {@inheritDoc}
     *
     * <p>Stated rather than derived, for the reason {@link RpcTest#configuration()} gives.
     */
    @Override
    public String configuration() {
        return "flagd-in-process";
    }

    @Override
    protected Config.Resolver resolver() {
        return Config.Resolver.IN_PROCESS;
    }

    @Override
    protected int backendPort() {
        return 8015;
    }

    /**
     * {@inheritDoc}
     *
     * <p>One entry, and only on this resolver. RPC evaluates in flagd, which has enforced the lossy
     * half of the coercion rule since v0.18.1; in-process evaluates here, and does not. The URL is
     * flagd's issue because that is where the rule is settled, not where this code is.
     *
     * <p>Delete the entry once the conversion checks that it loses nothing; the capability stays
     * declared either way, because the widening direction is correct already.
     */
    @Override
    public List<KnownDeviation> knownDeviations() {
        return Collections.singletonList(KnownDeviation.tracked(
                Capability.NUMERIC_COERCION,
                "https://github.com/open-feature/flagd/issues/1996",
                "The lossy half of the coercion rule is not enforced: evaluating float-flag (0.5) "
                        + "through the integer API returns 0 with no error code, rather than "
                        + "TYPE_MISMATCH with the code default, so the fractional part is discarded "
                        + "silently. FlagdCore converts a Double to an int with intValue() and no "
                        + "losslessness check, so the defect is in this repository's in-process "
                        + "evaluator rather than in flagd or in the transport. Widening an integer "
                        + "to a float is correct, which is why the capability is declared and the "
                        + "scenario left to fail rather than the capability withheld."));
    }
}
