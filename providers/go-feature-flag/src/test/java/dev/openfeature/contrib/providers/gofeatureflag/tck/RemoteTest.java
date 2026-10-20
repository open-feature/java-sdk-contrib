package dev.openfeature.contrib.providers.gofeatureflag.tck;

import dev.openfeature.contrib.providers.gofeatureflag.bean.EvaluationType;

/** Runs the OpenFeature Provider TCK against the GO Feature Flag provider in remote (OFREP) mode. */
public class RemoteTest extends AbstractGoFeatureFlagTest {

    @Override
    public String configuration() {
        return "go-feature-flag-remote";
    }

    @Override
    protected EvaluationType evaluationType() {
        return EvaluationType.REMOTE;
    }
}
