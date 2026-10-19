package dev.openfeature.contrib.tools.tck.internal.steps;

import dev.openfeature.contrib.tools.tck.BackendControl;
import dev.openfeature.contrib.tools.tck.ProviderTckHarness;
import io.cucumber.core.backend.ObjectFactory;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.LinkedHashSet;
import java.util.Set;
import org.picocontainer.MutablePicoContainer;
import org.picocontainer.PicoBuilder;

/**
 * Instantiates step classes, with the harness and backend control available for injection.
 *
 * <p>The container is per scenario; the {@link TckLifecycle} is per factory, started on first use.
 */
public final class TckObjectFactory implements ObjectFactory {

    private final Set<Class<?>> glue = new LinkedHashSet<>();

    private MutablePicoContainer pico;

    private TckLifecycle lifecycle;

    @Override
    public boolean addClass(Class<?> glueClass) {
        if (isInstantiable(glueClass) && glue.add(glueClass)) {
            addConstructorDependencies(glueClass);
        }
        return true;
    }

    /**
     * Registers the constructor parameter types a glue class needs, recursively.
     *
     * @param glueClass the class whose constructor parameters to register
     */
    private void addConstructorDependencies(Class<?> glueClass) {
        for (Constructor<?> constructor : glueClass.getConstructors()) {
            for (Class<?> parameter : constructor.getParameterTypes()) {
                addClass(parameter);
            }
        }
    }

    /**
     * Cucumber's own rule for a component: public, concrete, not an inner class.
     *
     * @param candidate the type to test
     * @return whether the container can instantiate it
     */
    private static boolean isInstantiable(Class<?> candidate) {
        boolean nonStaticInner = !Modifier.isStatic(candidate.getModifiers()) && candidate.getEnclosingClass() != null;
        return Modifier.isPublic(candidate.getModifiers())
                && !Modifier.isAbstract(candidate.getModifiers())
                && !nonStaticInner;
    }

    @Override
    public void start() {
        pico = new PicoBuilder().withCaching().build();
        if (lifecycle == null) {
            // empty when a launcher selects only the extension glue and never reaches @BeforeAll
            lifecycle = TckLifecycle.startForExecutingSuite().orElse(null);
        }
        if (lifecycle != null) {
            pico.addComponent(ProviderTckHarness.class, lifecycle.harness());
            pico.addComponent(BackendControl.class, lifecycle.backendControl());
        }
        for (Class<?> glueClass : glue) {
            pico.addComponent(glueClass);
        }
        pico.start();
    }

    @Override
    public void stop() {
        if (pico != null) {
            pico.stop();
            pico.dispose();
            pico = null;
        }
    }

    @Override
    public <T> T getInstance(Class<T> glueClass) {
        return pico.getComponent(glueClass);
    }
}
