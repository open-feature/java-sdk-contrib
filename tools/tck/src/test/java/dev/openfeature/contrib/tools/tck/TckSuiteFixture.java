package dev.openfeature.contrib.tools.tck;

import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.NoOpProvider;
import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * A concrete TCK suite, used by these tests for what the JUnit Platform makes of it.
 *
 * <p>Only ever <em>discovered</em>, never executed: discovery needs the annotations on
 * {@link ProviderTckTest} and nothing else, so these tests exercise the real suite configuration —
 * the real selectors, the real glue, the real engines — without Docker.
 *
 * <p>Extends {@link ContainerizedProviderTckTest} rather than {@link ProviderTckTest} directly, so
 * that the suite under discovery is shaped like the one an adopter with a real backend writes.
 *
 * <p>Deliberately not named {@code *Test}, so Surefire does not find it and try to run it. Running it
 * would start a Compose stack that does not exist.
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
