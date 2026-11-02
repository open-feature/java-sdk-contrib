package dev.openfeature.contrib.providers.flagd.tck;

import dev.openfeature.contrib.providers.flagd.Config;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.tools.tck.BackendEndpoint;
import dev.openfeature.contrib.tools.tck.Capability;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.apache.commons.io.FileUtils;

/**
 * Runs the OpenFeature Provider TCK against the flagd provider in file mode.
 *
 * <p>This resolver reads its ruleset from a file instead of a socket, so the stack has to put that
 * file where this JVM can see it. The testbed's Compose file mounts {@code $FLAGS_DIR} at
 * {@code /flags}, where its launchpad writes {@code allFlags.json}, so naming a host directory
 * through {@link #composeEnvironment()} is all it takes.
 */
public class FileTest extends AbstractResolverTest {

    /**
     * Where the testbed writes its flags and this provider reads them.
     *
     * <p>A fixed name under the module's gitignored {@code tmp/}, cleared on the way in, so a crashed
     * run leaves one directory behind instead of one more each time. Static because
     * {@code beginSuite()} validates the declarations on a throwaway harness with no stack.
     */
    private static final Path FLAGS_DIR = flagsDir();

    /** The file flagd's launchpad combines the ruleset into; see {@code launchpad/pkg/json.go}. */
    private static final String FLAG_FILE = "allFlags.json";

    /**
     * How often to restat the flag file.
     *
     * <p>Far below {@code Config.DEFAULT_OFFLINE_POLL_MS}, which is sane for a long-lived provider and
     * far too slow for a scenario: polling is the only way this resolver learns of a change, so
     * {@code @configuration-change} waits out a whole interval before it can pass.
     */
    private static final int POLL_INTERVAL_MS = 100;

    /**
     * {@inheritDoc}
     *
     * <p>Stated rather than derived, for the reason {@link RpcTest#configuration()} gives.
     */
    @Override
    public String configuration() {
        return "flagd-file";
    }

    @Override
    public Config.Resolver resolver() {
        return Config.Resolver.FILE;
    }

    /** flagd's sync listener; this resolver dials nothing, see {@link AbstractResolverTest}. */
    @Override
    protected int backendPort() {
        return 8015;
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, String> composeEnvironment() {
        return Collections.singletonMap("FLAGS_DIR", FLAGS_DIR.toString());
    }

    /** The endpoint is ignored: the ruleset arrives on the filesystem, not over a socket. */
    @Override
    protected FlagdOptions.FlagdOptionsBuilder flagSource(
            FlagdOptions.FlagdOptionsBuilder options, BackendEndpoint endpoint) {
        return options.offlineFlagSourcePath(FLAGS_DIR.resolve(FLAG_FILE).toString())
                .offlinePollIntervalMs(POLL_INTERVAL_MS);
    }

    /** A path rather than a closed port, which is what "unreachable" means to this resolver. */
    @Override
    protected FlagdOptions.FlagdOptionsBuilder missingFlagSource(FlagdOptions.FlagdOptionsBuilder options) {
        return options.offlineFlagSourcePath(FLAGS_DIR.resolve("not-existing").toString())
                .offlinePollIntervalMs(POLL_INTERVAL_MS);
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@link AbstractResolverTest#capabilities()} minus {@link Capability#STALE}. The provider can
     * go stale here, but the only outage the control API can stage is stopping flagd's listeners, and
     * a file already on disk survives that, so nothing would cause the stale event the scenario waits
     * for.
     *
     * <p>{@link Capability#CONFIGURATION_CHANGE} is declared: the control API rewrites the files in
     * the mounted volume, so the change reaches this provider through {@link #POLL_INTERVAL_MS}
     * rather than over a stream.
     */
    @Override
    public Set<Capability> capabilities() {
        return Capability.declarableExcept(Capability.REINITIALIZATION, Capability.STALE);
    }

    private static Path flagsDir() {
        Path directory = Paths.get("tmp", "tck-file");
        try {
            FileUtils.deleteDirectory(directory.toFile());
            return Files.createDirectories(directory).toAbsolutePath();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not prepare the directory the testbed writes its flags to", e);
        }
    }
}
