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
 * Instantiates step classes, with the harness and the backend control available for injection.
 *
 * <p>Cucumber's stock factory can only register classes it instantiates itself, so an
 * interface-typed constructor parameter can never be satisfied.
 *
 * <p>The container is per scenario, as the SPI specifies, which is what keeps {@code TckState}
 * from leaking between scenarios. The harness and the backend control are not rebuilt with it:
 * they are suite-scoped instances held by {@link TckLifecycle}, registered here by reference.
 */
public final class TckObjectFactory implements ObjectFactory {

    private final Set<Class<?>> glue = new LinkedHashSet<>();

    private MutablePicoContainer pico;

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
     * <p>{@code TckState} is a parameter, not glue, so without this no step class resolves.
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
        boolean nonStaticInner =
                !Modifier.isStatic(candidate.getModifiers()) && candidate.getEnclosingClass() != null;
        return Modifier.isPublic(candidate.getModifiers())
                && !Modifier.isAbstract(candidate.getModifiers())
                && !nonStaticInner;
    }

    @Override
    public void start() {
        pico = new PicoBuilder().withCaching().build();
        // Conditional: a launcher selecting only the extension glue never reaches @BeforeAll, so
        // there is no harness to register.
        if (TckLifecycle.isRunning()) {
            pico.addComponent(ProviderTckHarness.class, TckLifecycle.harness());
            pico.addComponent(BackendControl.class, TckLifecycle.backendControl());
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
