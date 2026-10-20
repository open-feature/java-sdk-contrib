package dev.openfeature.contrib.providers.gofeatureflag.tck;

import dev.openfeature.contrib.providers.gofeatureflag.bean.EvaluationType;

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
}
