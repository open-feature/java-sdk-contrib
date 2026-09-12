package dev.openfeature.contrib.providers.flagsmith;

import dev.openfeature.contrib.tools.providertck.BackendEndpoint;
import dev.openfeature.contrib.tools.providertck.Capability;
import dev.openfeature.contrib.tools.providertck.ContainerizedProviderTckTest;
import dev.openfeature.contrib.tools.providertck.KnownDeviation;
import dev.openfeature.sdk.FeatureProvider;
import java.io.File;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The OpenFeature Provider Conformance Suite against the Flagsmith <b>Java</b> provider.
 *
 * <p>An experiment rather than a finished adoption. The point is comparison: the Go adoption
 * (go-sdk-contrib#959) reports 31 pass / 2 fail / 19 skip, and the Python provider -- which lives
 * in the Flagsmith organisation rather than in a contrib repo -- reports 28 / 5 / 19 against the
 * same container. Three providers written by different people against the same backend API is
 * exactly the situation the cross-language suite exists for, and the Go and Python results already
 * disagree.
 *
 * <p>The backend is the same container both of those use:
 * <a href="https://github.com/aepfli/flagsmith-tck-testbed">aepfli/flagsmith-tck-testbed</a>, the
 * Flagsmith Edge Proxy with a launchpad implementing the control API. Nothing about it is
 * language-specific -- it is a container with an HTTP control API, which is the point of the
 * control API existing.
 */
public class FlagsmithProviderTckTest extends ContainerizedProviderTckTest {

    /**
     * Fixed by the testbed. The control API has no way to communicate connection parameters --
     * {@code POST /start} returns a bare 200 with no body -- so every adoption hardcodes these,
     * exactly as a flagd adoption hardcodes a port.
     */
    private static final String SERVER_SIDE_KEY = "ser.provider-tck-server-key";

    private static final int PROXY_PORT = 8000;

    @Override
    public File composeFile() {
        return new File("src/test/resources/flagsmith-testbed-compose.yaml");
    }

    @Override
    public List<Integer> backendPorts() {
        return Arrays.asList(PROXY_PORT);
    }

    @Override
    public FeatureProvider createProvider(BackendEndpoint endpoint) {
        // The Flagsmith SDK appends its own path segments, so baseUri is the API root with a
        // trailing slash: remote evaluation requests "flags/" beneath it.
        String baseUri = String.format(
                "http://%s:%d/api/v1/", endpoint.host(), endpoint.port(PROXY_PORT));

        FlagsmithProviderOptions options = FlagsmithProviderOptions.builder()
                .apiKey(SERVER_SIDE_KEY)
                .baseUri(baseUri)
                // usingBooleanConfigValue is the setting the three languages disagree about.
                //
                // Java and Go both default it to false, meaning a boolean flag resolves from
                // Flagsmith's feature_state_value. The Python provider defaults the OPPOSITE way:
                // it reads the `enabled` state and treats feature_state_value as opt-in.
                //
                // Set explicitly here, because the canonical set models booleans as values -- every
                // flag is seeded enabled, so reading `enabled` would resolve boolean-zero-flag to
                // true, which is exactly what the falsy-value scenario catches.
                .usingBooleanConfigValue(true)
                .build();
        return new FlagsmithProvider(options);
    }

    @Override
    public FeatureProvider createUnavailableProvider() {
        // Pointed at a closed port on localhost, never at the backend under test -- that has to
        // stay up, and simulated outages belong to the control API.
        FlagsmithProviderOptions options = FlagsmithProviderOptions.builder()
                .apiKey(SERVER_SIDE_KEY)
                .baseUri("http://localhost:9999/api/v1/")
                .usingBooleanConfigValue(true)
                .build();
        return new FlagsmithProvider(options);
    }

    /**
     * Predictions, to be corrected by the run.
     *
     * <p>{@code VARIANTS} is withheld for the reason the Go adoption established: Flagsmith has no
     * variant concept for a plain feature, the evaluation response carries no variant key, and no
     * seeding can produce one. That is permitted rather than defective -- 2.2.4 makes populating
     * the variant a SHOULD -- so it carries no deviation entry.
     *
     * <p>{@code LARGE_INTEGERS} is withheld here and declared in Go, and the difference is real
     * rather than an oversight: Java's integer accessor is a 32-bit {@code Integer}, so 2^53-1
     * cannot be asked for at all. This is the same reason Java withholds it for flagd.
     *
     * <p>The lifecycle and event capabilities are withheld pending the run. Go's provider
     * implements no {@code StateHandler} whatsoever; whether Java's does is the first thing this
     * run answers, and declaring them afterwards is the correct follow-up. Withholding a capability
     * a provider genuinely has is the expensive mistake, because it makes the suite blind to it.
     */
    @Override
    public Set<Capability> capabilities() {
        return EnumSet.of(Capability.OBJECT, Capability.TARGETING);
    }

    @Override
    public List<KnownDeviation> knownDeviations() {
        return Arrays.asList(KnownDeviation.untracked(
                Capability.NUMERIC_COERCION,
                "Withheld pending the run, and recorded as a prediction rather than a measurement. "
                        + "Flagsmith stores floats and objects as strings because feature_state_value is "
                        + "natively boolean, integer or string only. Go compensates by parsing the string in "
                        + "its Float accessor and Python does not, so Python cannot read a Flagsmith float at "
                        + "all. Which of the two Java resembles is what this run is for."));
    }
}
