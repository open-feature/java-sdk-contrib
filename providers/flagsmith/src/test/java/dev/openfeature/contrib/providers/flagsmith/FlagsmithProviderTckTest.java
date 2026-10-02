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
     * <p>{@code STRING_TYPING} is declared and {@code FULLY_TYPED_VALUES} withheld, which is the
     * split this backend motivated. {@code feature_state_value} is natively boolean, integer or
     * string: a boolean really is a boolean and an integer really is an integer, so both report
     * {@code TYPE_MISMATCH} through the String accessor, while a float and a structure have no
     * native type, are stored as text, and are correctly returned. Withheld rather than deviated
     * because nothing obliges a provider to report a type its store does not have.
     *
     * <p>{@code DISABLED_FLAGS} is declared. Flagsmith's native model is {@code enabled} plus a
     * value, so the canonical set's four disabled-* flags map straight onto it.
     *
     * <p>The lifecycle and event capabilities are withheld: this provider has no observable
     * initialisation for the suite to assert against.
     */
    @Override
    public Set<Capability> capabilities() {
        return EnumSet.of(
                Capability.OBJECT,
                Capability.TARGETING,
                Capability.DISABLED_FLAGS,
                Capability.STANDARD_REASONS,
                Capability.NUMERIC_COERCION,
                Capability.STRING_TYPING);
    }

    @Override
    public List<KnownDeviation> knownDeviations() {
        return Arrays.asList(
                KnownDeviation.untracked(
                        Capability.STANDARD_REASONS,
                        "This provider reports exactly one reason. resolveFlagsmithEvaluation returns "
                                + "Reason.DISABLED for a disabled flag and leaves the reason null on every "
                                + "successful resolution, so STATIC and TARGETING_MATCH are never reported. The "
                                + "resolved values are correct throughout -- only the reason is missing. The Go "
                                + "Flagsmith provider reports all three against the identical backend, which makes "
                                + "this a gap rather than a considered choice."),
                KnownDeviation.untracked(
                        Capability.NUMERIC_COERCION,
                        "Flagsmith stores floats as strings, and this provider does not parse them back. "
                                + "Both lossless coercion scenarios return the caller's default instead of the "
                                + "value, as does the untagged \"A float flag resolves as a float\". A Float "
                                + "accessor that parsed the stored string would fix all three; Go has one, "
                                + "Python and Java do not."));
    }
}
