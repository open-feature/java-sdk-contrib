package dev.openfeature.contrib.providers.gofeatureflag.tck;

import dev.openfeature.contrib.providers.gofeatureflag.bean.EvaluationType;
import dev.openfeature.contrib.tools.tck.Capability;
import dev.openfeature.contrib.tools.tck.KnownDeviation;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Runs the OpenFeature Provider TCK against the GO Feature Flag provider in in-process mode. */
public class InProcessTest extends AbstractGoFeatureFlagTest {

    @Override
    public String configuration() {
        return "go-feature-flag-in-process";
    }

    @Override
    protected EvaluationType evaluationType() {
        return EvaluationType.IN_PROCESS;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Everything declarable, and measured: 65 scenarios, 64 passing, and the one skip is
     * {@code @large-integers}, which the TCK refuses for every Java provider because the integer
     * accessor is 32-bit.
     *
     * <p>{@link Capability#NUMERIC_COERCION} passes in full, which is worth stating because the GO
     * Feature Flag engine itself narrows any float to an integer on an integer request. The WASM build
     * evaluates untyped here and this provider type-checks the result, so {@code float-flag} asked for
     * as an integer reports {@code TYPE_MISMATCH} rather than {@code 0}. The lossless integral-float
     * scenario passes without asserting much: the relay proxy serves {@code 10.0} as {@code 10}.
     */
    @Override
    public Set<Capability> capabilities() {
        return Capability.declarable();
    }

    @Override
    public List<KnownDeviation> knownDeviations() {
        return Collections.emptyList();
    }
}
