package dev.openfeature.contrib.tools.tck.internal;

import dev.openfeature.contrib.tools.tck.ProviderTckHarness;
import java.lang.reflect.Modifier;
import java.util.Optional;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tracks the currently executing TCK suite class so step definitions can find its harness.
 *
 * <p>Registered via {@code META-INF/services}; inert for non-TCK containers.
 */
public class TckSuiteListener implements TestExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(TckSuiteListener.class);

    private static volatile Class<? extends ProviderTckHarness> current;

    @Override
    public void executionStarted(TestIdentifier testIdentifier) {
        harnessClassOf(testIdentifier).ifPresent(suite -> {
            current = suite;
            log.debug("TCK suite started: {}", suite.getName());
        });
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult result) {
        harnessClassOf(testIdentifier).ifPresent(suite -> {
            if (suite.equals(current)) {
                current = null;
            }
        });
    }

    /**
     * Returns the suite class currently executing, if it is a TCK suite.
     *
     * @return the executing suite class, or empty when none is running or the listener was not
     *     registered
     */
    public static Optional<Class<? extends ProviderTckHarness>> currentSuite() {
        return Optional.ofNullable(current);
    }

    /**
     * Returns the TCK suite class a test identifier stands for, if it is one.
     *
     * @param testIdentifier the identifier to inspect
     * @return the concrete suite class, or empty when the identifier is not a TCK suite
     */
    public static Optional<Class<? extends ProviderTckHarness>> harnessClassOf(TestIdentifier testIdentifier) {
        return testIdentifier
                .getSource()
                .filter(ClassSource.class::isInstance)
                .map(ClassSource.class::cast)
                .flatMap(TckSuiteListener::loadClass)
                .filter(ProviderTckHarness.class::isAssignableFrom)
                .filter(candidate -> !Modifier.isAbstract(candidate.getModifiers()))
                .map(candidate -> candidate.asSubclass(ProviderTckHarness.class));
    }

    private static Optional<Class<?>> loadClass(ClassSource source) {
        try {
            // routine for sources from other engines; must not fail the run
            return Optional.of(source.getJavaClass());
        } catch (RuntimeException e) {
            log.trace("Ignoring unloadable class source {}", source, e);
            return Optional.empty();
        }
    }
}
