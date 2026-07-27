package dev.openfeature.contrib.tools.providertck;

import dev.openfeature.sdk.FeatureProvider;
import java.io.File;
import java.time.Duration;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The complete contract a provider author implements to run the OpenFeature Provider TCK.
 *
 * <p>Four methods have no default and must be supplied. Everything else is a convention with a
 * working default. If you find yourself needing to add lifecycle code, container handling or event
 * plumbing to your implementation, that is a bug in the TCK's base class rather than something to
 * work around here.
 *
 * <p>Implementations are discovered through {@link java.util.ServiceLoader}. Extend
 * {@link AbstractProviderTckTest} — which implements this interface and carries all the Cucumber
 * configuration — and register the concrete class in
 * {@code META-INF/services/dev.openfeature.contrib.tools.providertck.ProviderTckHarness}.
 *
 * <p>Example — the entire adoption for a provider:
 *
 * <pre>{@code
 * public class MyProviderTckTest extends AbstractProviderTckTest {
 *
 *     @Override
 *     public File composeFile() {
 *         return new File("src/test/resources/tck/docker-compose.yaml");
 *     }
 *
 *     @Override
 *     public List<Integer> backendPorts() {
 *         return Collections.singletonList(8013);
 *     }
 *
 *     @Override
 *     public FeatureProvider createProvider(BackendEndpoint endpoint) {
 *         return new MyProvider(endpoint.host(), endpoint.port(8013));
 *     }
 *
 *     @Override
 *     public FeatureProvider createUnavailableProvider() {
 *         return new MyProvider("localhost", 9999);
 *     }
 * }
 * }</pre>
 */
public interface ProviderTckHarness {

    /**
     * Returns the Docker Compose file describing the backend stack under test.
     *
     * <p>The path is resolved relative to the Maven module directory, so
     * {@code new File("src/test/resources/tck/docker-compose.yaml")} is the idiomatic form.
     *
     * <p>The stack is started once before the first scenario and stopped after the last one. It is
     * never restarted in between — see {@link #createProvider(BackendEndpoint)} and the
     * no-container-restart invariant documented in {@code openapi/control-api.yaml}. The stack must
     * not pin host ports.
     *
     * @return the Compose file describing the backend stack
     */
    File composeFile();

    /**
     * Returns the container-internal ports on {@link #backendService()} that the provider connects
     * to, so Testcontainers can expose and map them.
     *
     * <p>The control API port from {@link #controlPort()} is exposed automatically and does not
     * need to be listed here.
     *
     * @return container-internal ports the provider connects to
     */
    List<Integer> backendPorts();

    /**
     * Creates the provider under test, configured against the running backend.
     *
     * <p>Called after the Compose stack is up and the control API has seeded the canonical flag
     * set. The endpoint carries the dynamically mapped host ports, which is why this is a factory
     * rather than a field: the ports do not exist until the stack has started.
     *
     * <p>The TCK owns the provider lifecycle from here — it registers the provider with the
     * OpenFeature API under a scenario-scoped domain, waits for it to become ready, and shuts it
     * down afterwards. Do not call {@code setProvider} or {@code initialize} yourself.
     *
     * @param endpoint host and mapped ports of the running backend stack
     * @return a configured, uninitialised provider
     */
    FeatureProvider createProvider(BackendEndpoint endpoint);

    /**
     * Creates a provider pointed at a backend that does not exist.
     *
     * <p>Used by the initialisation-failure scenarios, which assert that a provider that cannot
     * reach its backend settles into {@code ERROR} and emits {@code PROVIDER_ERROR} rather than
     * hanging or throwing out of {@code setProvider}.
     *
     * <p>Point this at a closed port on localhost. Do not point it at the Compose stack — the stack
     * must stay up and reachable, and simulated outages belong to the control API.
     *
     * <p>Configure a short connection deadline. The scenario allows a bounded time for the error
     * event to arrive, and a provider with a 30-second connect timeout will not make it.
     *
     * @return a configured provider that cannot reach a backend
     */
    FeatureProvider createUnavailableProvider();

    /**
     * Declares which optional parts of the provider contract this provider supports.
     *
     * <p>Scenarios tagged with a capability that is not in this set are reported as
     * <strong>skipped</strong>. They are never silently passed.
     *
     * <p>Defaults to every capability. Narrow it rather than widening it: start from the default,
     * run the suite, and remove only what your provider genuinely cannot do.
     *
     * @return the capabilities this provider supports
     */
    default Set<Capability> capabilities() {
        return EnumSet.allOf(Capability.class);
    }

    /**
     * Returns the Compose service name that hosts the control API and the backend the provider
     * connects to.
     *
     * @return the Compose service name, {@code backend} by default
     */
    default String backendService() {
        return "backend";
    }

    /**
     * Returns the container-internal port the control API listens on.
     *
     * @return the control API port, {@code 8080} by default
     */
    default int controlPort() {
        return 8080;
    }

    /**
     * Returns extra services and container-internal ports to expose, for stacks that contain more
     * than the backend service.
     *
     * <p>Keys are Compose service names, values are container-internal ports. Resolve the mapped
     * ports with {@link BackendEndpoint#port(String, int)}.
     *
     * @return additional services and ports to expose, empty by default
     */
    default Map<String, List<Integer>> additionalExposedPorts() {
        return Collections.emptyMap();
    }

    /**
     * Returns the control API configuration name used to seed the canonical flag set.
     *
     * @return the configuration name passed to {@code POST /start}, {@code default} by default
     */
    default String defaultConfig() {
        return "default";
    }

    /**
     * Returns how long to wait for the Compose stack to become reachable.
     *
     * @return the stack startup timeout, 60 seconds by default
     */
    default Duration startupTimeout() {
        return Duration.ofSeconds(60);
    }

    /**
     * Returns how long to wait for a provider event to arrive.
     *
     * <p>This is the single most important knob for a provider author, because providers observe
     * backend changes on wildly different timescales. A streaming provider sees a configuration
     * change in milliseconds; a provider that polls every 30 seconds may need most of a poll
     * interval before it notices. Set this to comfortably exceed your worst-case detection latency,
     * or the suite will report timeouts that are really just impatience.
     *
     * <p>Individual scenarios can tighten this with the explicit
     * {@code within {int}ms} step, which always wins over this value.
     *
     * @return the default event await timeout, 12 seconds by default
     */
    default Duration eventTimeout() {
        return Duration.ofSeconds(12);
    }

    /**
     * Returns how long to wait for a provider to reach {@code READY} during initialisation.
     *
     * @return the readiness timeout, 30 seconds by default
     */
    default Duration readyTimeout() {
        return Duration.ofSeconds(30);
    }

    /**
     * Returns how long to pause after a control API call before continuing.
     *
     * <p>Covers the gap between the control API acknowledging a command and the backend actually
     * having acted on it. Raise it if you see flakiness immediately after
     * {@code the flag was modified} or a provider setup step.
     *
     * @return the settle time, 50 milliseconds by default
     */
    default Duration settleTime() {
        return Duration.ofMillis(50);
    }
}
