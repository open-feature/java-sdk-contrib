package dev.openfeature.contrib.providers.flagsmith;

import dev.openfeature.contrib.tools.tck.BackendEndpoint;
import dev.openfeature.contrib.tools.tck.Capability;
import dev.openfeature.contrib.tools.tck.ContainerizedProviderTckTest;
import dev.openfeature.contrib.tools.tck.KnownDeviation;
import dev.openfeature.sdk.FeatureProvider;
import java.io.File;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The OpenFeature Provider Conformance Suite against the Flagsmith <b>Java</b> provider.
 *
 * <p>The backend, and how the four language adoptions compare against it, are documented once in
 * <a href="https://github.com/aepfli/flagsmith-tck-testbed">aepfli/flagsmith-tck-testbed</a> rather
 * than restated in each adoption.
 */
public class FlagsmithProviderTckTest extends ContainerizedProviderTckTest {

    /** Fixed by the testbed, because the control API cannot hand connection parameters to a provider. */
    private static final String SERVER_SIDE_KEY = "ser.provider-tck-server-key";

    private static final int PROXY_PORT = 8000;

    @Override
    public File composeFile() {
        return new File("src/test/resources/tck/docker-compose.yaml");
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
     * <p>{@code STANDARD_REASONS} and {@code NUMERIC_COERCION} are declared and both fail: this
     * provider attempts each and gets it wrong, so the failures belong in the results.
     *
     * <p>{@code DISABLED_FLAGS} is declared. Flagsmith's native model is {@code enabled} plus a
     * value, so the canonical set's four disabled-* flags map straight onto it.
     *
     * <p>The lifecycle and event capabilities are withheld pending the run. Go's provider
     * implements no {@code StateHandler} whatsoever; whether Java's does is the first thing this
     * run answers, and declaring them afterwards is the correct follow-up. Withholding a capability
     * a provider genuinely has is the expensive mistake, because it makes the suite blind to it.
     */
    @Override
    public Set<Capability> capabilities() {
        return EnumSet.of(
                Capability.OBJECT,
                Capability.TARGETING,
                Capability.DISABLED_FLAGS,
                Capability.STANDARD_REASONS,
                Capability.NUMERIC_COERCION);
    }

    @Override
    public List<KnownDeviation> knownDeviations() {
        return Arrays.asList(
                KnownDeviation.untracked(
                        Capability.STANDARD_REASONS,
                        "This provider reports exactly one reason. resolveFlagsmithEvaluation returns "
                                + "Reason.DISABLED for a disabled flag and leaves the reason null on every "
                                + "successful resolution, so STATIC and TARGETING_MATCH are never reported. The "
                                + "resolved values are correct throughout -- only the reason is missing. The capability is declared and "
                                + "the scenarios fail rather than skip, because the provider does build a resolution "
                                + "and simply leaves the field out, so running them establishes something. The Go "
                                + "Flagsmith provider reports STATIC, DISABLED and TARGETING_MATCH against the "
                                + "identical backend, which is what makes this a gap rather than a considered choice."),
                KnownDeviation.untracked(
                        null,
                        "float-flag requested as a String resolves to \"0.5\" rather than reporting TYPE_MISMATCH, in "
                                + "an untagged row of the wrong-type outline, and object-flag as a String resolves to "
                                + "the raw JSON text beside it. Flagsmith has no float type and no object type -- "
                                + "feature_state_value is natively boolean, integer or string -- so on this backend "
                                + "both really are strings and neither request is a type mismatch. Recorded because "
                                + "the scenarios are mandatory and fail, not because the provider is wrong: whether "
                                + "the type-mismatch matrix is satisfiable against a backend with a coarser type "
                                + "system is an open question for the suite. All four language adoptions fail these."),
                KnownDeviation.untracked(
                Capability.NUMERIC_COERCION,
                "Withheld pending the run, and recorded as a prediction rather than a measurement. "
                        + "Flagsmith stores floats and objects as strings because feature_state_value is "
                        + "natively boolean, integer or string only. Go compensates by parsing the string in "
                        + "its Float accessor and Python does not, so Python cannot read a Flagsmith float at "
                        + "all. Which of the two Java resembles is what this run is for."));
    }
}
