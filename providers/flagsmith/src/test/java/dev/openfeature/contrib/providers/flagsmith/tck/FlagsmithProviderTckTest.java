package dev.openfeature.contrib.providers.flagsmith.tck;

import dev.openfeature.contrib.providers.flagsmith.FlagsmithProvider;
import dev.openfeature.contrib.providers.flagsmith.FlagsmithProviderOptions;
import dev.openfeature.contrib.tools.tck.BackendEndpoint;
import dev.openfeature.contrib.tools.tck.Capability;
import dev.openfeature.contrib.tools.tck.ContainerizedProviderTckTest;
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
        String baseUri = String.format("http://%s:%d/api/v1/", endpoint.host(), endpoint.port(PROXY_PORT));

        FlagsmithProviderOptions options = FlagsmithProviderOptions.builder()
                .apiKey(SERVER_SIDE_KEY)
                .baseUri(baseUri)
                // Set explicitly, not left to the default: every canonical flag is seeded enabled,
                // so reading `enabled` instead of feature_state_value would resolve
                // boolean-zero-flag to true -- exactly what the falsy-value scenario catches.
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
     * {@inheritDoc}
     *
     * <p>{@code VARIANTS} is withheld because Flagsmith has no variant concept for a plain feature:
     * the evaluation response carries no variant key and no seeding can produce one. Permitted rather
     * than defective, so it carries no deviation entry.
     *
     * <p>{@code LARGE_INTEGERS} is withheld because Java's integer accessor is a 32-bit
     * {@code Integer}, so 2^53-1 cannot be asked for at all. Go declares it; the difference is real
     * rather than an oversight.
     *
     * <p>{@code STANDARD_REASONS} is withheld: {@code resolveFlagsmithEvaluation} sets
     * {@code Reason.DISABLED} for a disabled flag and leaves the reason null on every successful
     * resolution, so STATIC, TARGETING_MATCH and DEFAULT are never reported. The Go Flagsmith provider
     * reports all three against the same backend.
     *
     * <p>{@code NUMERIC_COERCION} is withheld: this provider does not coerce between Integer and Float
     * requests.
     *
     * <p>{@code OBJECT} is withheld: Flagsmith has no structured type and stores the JSON document as
     * text, which this provider does not parse into a structure.
     *
     * <p>{@code STRING_TYPING} is declared and {@code FULLY_TYPED_VALUES} withheld, which is the split
     * this backend motivated. {@code feature_state_value} is natively boolean, integer or string, so
     * a boolean and an integer both report {@code TYPE_MISMATCH} through the String accessor, while a
     * float and a structure have no native type, are stored as text, and are correctly returned.
     * Withheld rather than deviated because nothing obliges a provider to report a type its store does
     * not have.
     *
     * <p>{@code DISABLED_FLAGS} is declared: Flagsmith's native model is {@code enabled} plus a value,
     * so the canonical set's four disabled-* flags map straight onto it.
     *
     * <p>The lifecycle and event capabilities are withheld: this provider has no observable
     * initialisation for the suite to assert against.
     */
    @Override
    public Set<Capability> capabilities() {
        return EnumSet.of(Capability.TARGETING, Capability.DISABLED_FLAGS, Capability.STRING_TYPING);
    }
}
