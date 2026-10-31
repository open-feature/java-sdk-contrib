package dev.openfeature.contrib.providers.flagd.tck;

import dev.openfeature.contrib.providers.flagd.Config;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.tools.tck.BackendEndpoint;
import dev.openfeature.contrib.tools.tck.Capability;
import dev.openfeature.contrib.tools.tck.TckExcludeTags;
import dev.openfeature.contrib.tools.tck.TckIncludeTags;
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
 * <p>The third resolver reads its ruleset from a file instead of a socket, so the stack has to put
 * that file somewhere this JVM can see it. The testbed's Compose file mounts {@code $FLAGS_DIR} at
 * {@code /flags}, where its launchpad writes {@code allFlags.json}, so naming a host directory
 * through {@link #composeEnvironment()} is all it takes -- the same arrangement the legacy e2e suite
 * reaches through {@code ContainerEntry}.
 *
 * <p>Three exclusions, each measured rather than assumed:
 *
 * <ul>
 *   <li>{@code @ext-connection} -- every scenario in {@code connection.feature} and
 *       {@code events.feature} turns on a gRPC connection: a custom certificate, a lost stream, a 403
 *       from envoy. This resolver has none, so five of them time out waiting for an error that the
 *       stack cannot cause and one ("Connection with a custom certificate") passes without the
 *       certificate having been used for anything. The sixth, "Provider unavailable", does hold, but
 *       its 3000ms bound was measured against a refused gRPC dial; here the error is the 2s retry
 *       grace period, and the canonical {@code lifecycle.feature} asks the same thing with room.
 *   <li>{@code @metadata-provider} -- these name a {@code selector-} flag file, and the launchpad
 *       deliberately leaves those out of the combined {@code allFlags.json}
 *       ({@code CombineJSONFiles} skips the prefix), so there is no flag set to read metadata from.
 *   <li>{@code @fractional-v2} -- as in the other two suites: the Java evaluator buckets with v3.
 * </ul>
 */
@TckIncludeTags("@file")
@TckExcludeTags({"@ext-connection", "@metadata-provider", "@fractional-v2"})
public class FileTest extends AbstractResolverTest {

    /**
     * Where the testbed writes its flags and this provider reads them.
     *
     * <p>Under the module's gitignored {@code tmp/}, like the legacy suite's, and a fixed name
     * cleared on the way in rather than a random one, so a crashed run leaves one directory behind
     * instead of one more each time. Static: {@code beginSuite()} builds a throwaway harness just to
     * validate the declarations, and it has no stack to point anywhere.
     */
    private static final Path FLAGS_DIR = flagsDir();

    /** The file flagd's launchpad combines the ruleset into; see {@code launchpad/pkg/json.go}. */
    private static final String FLAG_FILE = "allFlags.json";

    /**
     * How often to restat the flag file.
     *
     * <p>Far below {@code Config.DEFAULT_OFFLINE_POLL_MS} of 5000, which is a sane default for a
     * long-lived provider and far too slow for a scenario: polling is the only way this resolver
     * learns of a change, so {@code @configuration-change} waits out one whole interval before it
     * can pass, against an {@code eventTimeout()} of 12 seconds.
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
     * <p>{@link AbstractResolverTest#capabilities()} minus {@link Capability#STALE}, measured the
     * same way. A first run declared everything but {@code @reinitialization} and deselected nothing:
     * 353 scenarios, 302 passing, 19 failing, and every one of the 19 was either an extension scenario
     * written for a socket or the single {@code @stale} scenario below. With the exclusions above and
     * {@code STALE} withheld, 353 scenarios, 290 passing, 63 skipped, nothing failing -- 62 of the 65
     * canonical scenarios among them, the other three being the two withheld capabilities and
     * {@code @large-integers}.
     *
     * <p>{@link Capability#STALE} is withheld because its scenario -- losing the backend and getting
     * it back -- asks about something this resolver does not have. The provider can go stale: a
     * {@code FileQueueSource} that cannot read its file reports it, {@code FlagStore} maps that to
     * {@code STALE} and {@code onError} emits {@code PROVIDER_STALE} exactly as for the other two.
     * But the only outage the control API can stage is stopping flagd's listeners, and a file already
     * on disk survives that, so the scenario timed out waiting for a stale event that nothing was
     * going to cause. {@link dev.openfeature.contrib.tools.tck.BackendControl#disconnect()} has
     * nothing to act on here, which is the same statement from the other end.
     *
     * <p>{@link Capability#CONFIGURATION_CHANGE} is declared, and it is the one worth checking rather
     * than assuming: the control API rewrites the files in the mounted volume, so the change reaches
     * this provider through {@link #POLL_INTERVAL_MS} rather than over a stream, and the scenario
     * passes -- the event and the changed evaluation both.
     *
     * <p>{@link Capability#REINITIALIZATION} is withheld for the reason
     * {@link AbstractResolverTest#capabilities()} gives; it is a fact about {@code FlagdProvider},
     * which all three resolvers share.
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
