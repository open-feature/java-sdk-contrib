package dev.openfeature.contrib.tools.tck;

import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.NoOpProvider;
import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * A concrete containerised suite, only ever discovered (never run) by these tests. Not named
 * {@code *Test} so Surefire skips it.
 */
public class TckSuiteFixture extends ContainerizedProviderTckTest {

    @Override
    public File composeFile() {
        return new File("src/test/resources/there-is-no-stack.yaml");
    }

    @Override
    public List<Integer> backendPorts() {
        return Collections.singletonList(8013);
    }

    @Override
    public FeatureProvider createProvider(BackendEndpoint endpoint) {
        return new NoOpProvider();
    }

    @Override
    public FeatureProvider createUnavailableProvider() {
        return new NoOpProvider();
    }
}
