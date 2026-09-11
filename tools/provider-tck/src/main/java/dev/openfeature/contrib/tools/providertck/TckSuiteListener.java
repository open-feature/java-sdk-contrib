package dev.openfeature.contrib.tools.providertck;

import java.lang.reflect.Modifier;
import java.util.Optional;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tracks which TCK suite is currently executing, so the step definitions can find its harness.
 *
 * <p>The problem this solves: Cucumber's {@code @BeforeAll} is static and carries no information
 * about which suite triggered it. A provider with more than one transport — flagd has RPC and
 * in-process — therefore has no way to tell the glue which of its harnesses to use. Discovering
 * harnesses through {@link java.util.ServiceLoader} alone makes that ambiguous the moment a second
 * one is registered, and resolving it with a system property would push a separate Surefire
 * execution per mode onto every adopter's POM.
 *
 * <p>Instead: a concrete suite class <em>is</em> a {@link ProviderTckHarness}, and the JUnit
 * Platform tells us which one is running. This listener watches for a container whose source is a
 * concrete class implementing the SPI and records it for the duration of that suite's execution.
 * Adding a second mode is then a second class and nothing else — no registration file, no system
 * property, no build configuration.
 *
 * <p>Registered through {@code META-INF/services/org.junit.platform.launcher.TestExecutionListener}
 * inside this JAR, so it is picked up automatically by Surefire, Gradle and IDEs. It ignores every
 * container that is not a TCK suite, so it is inert in builds that do not use the TCK.
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
    static Optional<Class<? extends ProviderTckHarness>> currentSuite() {
        return Optional.ofNullable(current);
    }

    /**
     * Returns the TCK suite class a test identifier stands for, if it is one.
     *
     * @param testIdentifier the identifier to inspect
     * @return the concrete suite class, or empty when the identifier is not a TCK suite
     */
    static Optional<Class<? extends ProviderTckHarness>> harnessClassOf(TestIdentifier testIdentifier) {
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
            // ClassSource resolves the class lazily and throws when it cannot be loaded — which is
            // routine for sources belonging to other engines, so it must not fail the run.
            return Optional.of(source.getJavaClass());
        } catch (RuntimeException e) {
            log.trace("Ignoring unloadable class source {}", source, e);
            return Optional.empty();
        }
    }
}
