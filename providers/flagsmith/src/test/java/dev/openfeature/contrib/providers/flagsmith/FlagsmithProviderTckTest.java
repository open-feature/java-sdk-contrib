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
     * <p><strong>{@code STRING_TYPING} is declared and {@code FULLY_TYPED_VALUES} is withheld, and
     * this is the backend that pair of capabilities was created for.</strong> The previous pass
     * measured all four scenarios under one tag: {@code boolean-flag} and {@code integer-flag}
     * reported {@code TYPE_MISMATCH} correctly, while {@code float-flag} through the String accessor
     * resolved to {@code "0.5"} and {@code object-flag} to its raw JSON text. That split is exactly
     * Flagsmith's type system: {@code feature_state_value} is natively boolean, integer or string,
     * so a boolean flag really is a boolean and an integer really is an integer — but a float and a
     * structure have no native type and are <em>stored as strings</em>. Asked for as strings, they
     * are returned, and that is the resolved flag value.
     *
     * <p>So the declaration now follows the backend rather than averaging over it. Specification
     * revision {@code bda599f1} split the tag along that line — {@code @string-typing} keeps the
     * boolean and integer rows, {@code @fully-typed-values} takes the float and the structure — and
     * this provider declares the first and withholds the second. The reason for withholding is a
     * fact about the store and not about the provider: Flagsmith records no native float or
     * structure type, so there is no mismatch here to report.
     *
     * <p>Withholding rather than a {@link KnownDeviation}, which is the opposite call from
     * {@code NUMERIC_COERCION} above, and Appendix F's declaring rules are what separate them. The
     * rule about scenarios being askable is subordinate to a prior question — whether the provider
     * owes an answer at all — and for these tags it does not: {@code TYPE_MISMATCH} is obliged by no
     * requirement, and the only normative statement about value type is Requirement 1.3.4, a
     * {@code SHOULD} on the client. Where the specification permits declining, withholding is the
     * honest report however askable the scenarios are, and a deviation would assert a defect that
     * does not exist. {@code NUMERIC_COERCION} is declared because flagd's ADR is a rule this suite
     * binds providers to; nothing binds Flagsmith to report a type its backend does not have.
     *
     * <p>The cost the previous pass recorded is gone, and recording it is what removed it. Under one
     * tag, withholding skipped the two scenarios this provider gets right along with the two it does
     * not, and that note ended "revisit if the capability is ever split by flag type". It was — the
     * measurement above is cited in Appendix F as the case that motivated the split, alongside a
     * language that failed the boolean and integer rows and would have had that defect published as
     * a permitted absence. Declaring the narrower tag now holds this provider to the two questions
     * its backend can answer.
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
                Capability.NUMERIC_COERCION,
                // STRING_TYPING without FULLY_TYPED_VALUES: the two scenarios the second gates
                // carry the first as well, so this runs the boolean and integer rows and leaves
                // the float and structured ones skipped. See capabilities()' javadoc.
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
                                + "resolved values are correct throughout -- only the reason is missing. The capability is declared and "
                                + "the scenarios fail rather than skip, because the provider does build a resolution "
                                + "and simply leaves the field out, so running them establishes something. The Go "
                                + "Flagsmith provider reports STATIC, DISABLED and TARGETING_MATCH against the "
                                + "identical backend, which is what makes this a gap rather than a considered choice."),
                KnownDeviation.untracked(
                        Capability.NUMERIC_COERCION,
                        "Java resembles Python rather than Go, which is what the run settled. Flagsmith "
                                + "stores floats as strings because feature_state_value is natively boolean, "
                                + "integer or string only; Go compensates by parsing that string in its Float "
                                + "accessor and Python does not. Neither does Java: both lossless coercion "
                                + "scenarios return the caller's default rather than the value -- "
                                + "integral-float-flag through the integer accessor gives 1 for 10, and "
                                + "integer-flag through the float accessor gives 0.1 for 10.0 -- and so does "
                                + "the untagged \"A float flag resolves as a float\", which gives 0.1 for 0.5. "
                                + "The capability is declared and left failing rather than withheld, because "
                                + "the provider does attempt the coercion; a Float accessor that parsed the "
                                + "stored string would fix all three at once."));
    }
}
