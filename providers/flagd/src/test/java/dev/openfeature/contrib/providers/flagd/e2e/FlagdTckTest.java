package dev.openfeature.contrib.providers.flagd.e2e;

import dev.openfeature.contrib.providers.flagd.Config;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.FlagdProvider;
import dev.openfeature.contrib.tools.providertck.AbstractProviderTckTest;
import dev.openfeature.contrib.tools.providertck.BackendEndpoint;
import dev.openfeature.contrib.tools.providertck.Capability;
import dev.openfeature.sdk.FeatureProvider;
import java.io.File;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Runs the OpenFeature Provider TCK against the flagd provider in RPC mode.
 *
 * <p>The entire adoption is this class plus {@code src/test/resources/tck/docker-compose.yaml} and
 * a one-line {@code META-INF/services} registration. Everything else — the Compose lifecycle, port
 * discovery, control API calls, provider registration, event awaiting — belongs to the TCK.
 *
 * <p>To also cover in-process mode, copy this class, change the resolver, and register both; then
 * select one per Surefire execution with {@code -Dopenfeature.tck.harness=<simple class name>}.
 */
public class FlagdTckTest extends AbstractProviderTckTest {

    private static final int RPC_PORT = 8013;

    @Override
    public File composeFile() {
        return new File("src/test/resources/tck/docker-compose.yaml");
    }

    @Override
    public List<Integer> backendPorts() {
        return Collections.singletonList(RPC_PORT);
    }

    @Override
    public FeatureProvider createProvider(BackendEndpoint endpoint) {
        return new FlagdProvider(FlagdOptions.builder()
                .resolverType(Config.Resolver.RPC)
                .host(endpoint.host())
                .port(endpoint.port(RPC_PORT))
                .deadline(1000)
                .retryGracePeriod(2)
                .retryBackoffMs(500)
                .build());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Everything except {@link Capability#STRICT_NUMERIC_TYPING}. Evaluating {@code float-flag}
     * (0.5) through the integer API returns {@code 0} with <em>no</em> error code rather than
     * {@code TYPE_MISMATCH} with the code default — the value is silently truncated. That is a
     * defect to fix, not a design choice; this line should be deleted once it is.
     */
    @Override
    public Set<Capability> capabilities() {
        return EnumSet.complementOf(EnumSet.of(Capability.STRICT_NUMERIC_TYPING));
    }

    @Override
    public FeatureProvider createUnavailableProvider() {
        return new FlagdProvider(FlagdOptions.builder()
                .resolverType(Config.Resolver.RPC)
                .host("localhost")
                .port(9999)
                .deadline(1000)
                .build());
    }
}
