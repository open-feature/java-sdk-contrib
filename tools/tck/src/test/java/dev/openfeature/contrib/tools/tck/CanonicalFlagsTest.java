package dev.openfeature.contrib.tools.tck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.providers.memory.InMemoryProvider;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins the in-process flag set to the definition this artifact packages.
 *
 * <p>This is the test that makes {@link CanonicalFlags} worth having. The flag set the in-memory
 * self-tests run against used to be written out as Java literals, and the failure mode of that was
 * quiet: a rename or a retyped value in {@code flags/canonical-flags.json} left the suite verifying
 * itself against a second, private baseline, so it reported green having tested the wrong flags. The
 * only way to catch that is to read the packaged file <em>independently of the decoder</em> and hold
 * the decoded set against it, which is what happens below.
 *
 * <p>The comparison is deliberately not a value table written out here. A table is another
 * transcription, drifts the same way, and a test that compares two copies of the same mistake passes.
 * Every expectation comes out of the file instead: the keys it defines, and for each the state it is
 * in and the value of the variant it says the flag resolves to. The state matters as much as the
 * value now that the set contains four {@code disabled-*} flags, which resolve to nothing at all —
 * a decoder that ignored {@code state} would serve their configured values and look correct against
 * a variant table.
 *
 * <p>The values are read back through an {@link InMemoryProvider} rather than off the decoded map,
 * because the provider matches a variant against the type of the accessor it was asked through. That
 * is what makes the types load-bearing and what this asserts: a JSON float is fetched through
 * {@code getDoubleEvaluation} and a JSON integer through {@code getIntegerEvaluation}, so a decoder
 * that turned {@code 10.0} into the integer {@code 10} — the mistake the file's own comment warns
 * about, because it lets the lossless-coercion scenario pass without coercing anything — fails here
 * with a type mismatch rather than sailing through a value comparison.
 */
class CanonicalFlagsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** The {@code flags} object of the packaged definition, read without going through the decoder. */
    private static Map<String, JsonNode> packaged;

    @BeforeAll
    static void readThePackagedDefinition() throws IOException {
        try (InputStream in = CanonicalFlagsTest.class.getClassLoader().getResourceAsStream(CanonicalFlags.RESOURCE)) {
            assertThat(in)
                    .as(
                            "%s must be packaged on the classpath by the copy-provider-tck-flags execution",
                            CanonicalFlags.RESOURCE)
                    .isNotNull();

            JsonNode flags = MAPPER.readTree(in).path("flags");
            assertThat(flags.isObject()).isTrue();

            packaged = new LinkedHashMap<>();
            for (Iterator<Map.Entry<String, JsonNode>> it = flags.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> entry = it.next();
                if (!"$comment".equals(entry.getKey())) {
                    packaged.put(entry.getKey(), entry.getValue());
                }
            }
        }
        assertThat(packaged)
                .as("the packaged definition has to define flags for any of this to mean anything")
                .isNotEmpty();
    }

    @Test
    @DisplayName("the decoded flag set defines exactly the keys the packaged definition defines")
    void theDecodedSetHasExactlyThePackagedKeys() {
        assertThat(CanonicalFlags.flagSet().keySet())
                .as("a key in one and not the other is the drift this replaced a transcription to prevent")
                .containsExactlyInAnyOrderElementsOf(packaged.keySet())
                .as("$comment is documentation, not a flag")
                .doesNotContain("$comment");
    }

    @Test
    @DisplayName("every flag resolves as its packaged state and default variant say it should")
    void everyFlagResolvesToItsPackagedDefaultVariant() throws Exception {
        InMemoryProvider provider = new InProcessBackendControl().createProvider();
        provider.initialize(new ImmutableContext());
        ImmutableContext context = new ImmutableContext();

        List<String> checked = new ArrayList<>();
        List<String> disabled = new ArrayList<>();
        for (Map.Entry<String, JsonNode> entry : packaged.entrySet()) {
            String key = entry.getKey();
            String defaultVariant = entry.getValue().path("defaultVariant").asText(null);
            assertThat(defaultVariant)
                    .as("%s names no defaultVariant, so the definition itself is broken", key)
                    .isNotNull();

            JsonNode expected = entry.getValue().path("variants").path(defaultVariant);
            assertThat(expected.isMissingNode())
                    .as("%s resolves to variant '%s', which the definition does not define", key, defaultVariant)
                    .isFalse();

            String state = entry.getValue().path("state").asText(null);
            assertThat(state)
                    .as("%s names no state, so the definition itself is broken", key)
                    .isIn("ENABLED", "DISABLED");

            boolean enabled = "ENABLED".equals(state);
            assertResolves(provider, context, key, defaultVariant, expected, enabled);
            checked.add(key);
            if (!enabled) {
                disabled.add(key);
            }
        }

        assertThat(checked)
                .as("the loop must actually have run over the packaged flags")
                .hasSameSizeAs(packaged.keySet());

        assertThat(disabled)
                .as("the disabled half of the assertion has to have been exercised, or a decoder that "
                        + "dropped the state would pass here unnoticed")
                .isNotEmpty();
    }

    /**
     * Resolves one flag through the accessor its packaged type calls for, and checks the value.
     *
     * <p>The accessor is chosen from the JSON type rather than from the decoded one, so the decoding
     * is being held against the file rather than asked to agree with itself. The default handed to
     * the accessor is deliberately never the expected value: {@code boolean-zero-flag} resolves to
     * {@code false} and {@code string-zero-flag} to {@code ""}, and a comparison whose fallback
     * happened to equal the answer would pass on a flag that had gone missing.
     *
     * <p>That property is also what lets one dispatch serve both states. A flag the definition marks
     * {@code DISABLED} resolves to nothing at all — the caller's default stands in — so the expected
     * answer is precisely the fallback this already had to pick to be distinct, and {@code enabled}
     * only chooses which of the two the resolution must equal. The four {@code disabled-*} flags are
     * the whole reason the parameter exists: a decoder that dropped {@code state} on the floor would
     * make them serve their configured values, and <em>that</em> is what fails here rather than
     * later, in a scenario, against a provider that did nothing wrong.
     */
    private static void assertResolves(
            InMemoryProvider provider,
            ImmutableContext context,
            String key,
            String variant,
            JsonNode expected,
            boolean enabled) {

        String where = key + " variant '" + variant + "'" + (enabled ? "" : ", disabled so the default stands in");
        switch (expected.getNodeType()) {
            case BOOLEAN:
                boolean bool = expected.booleanValue();
                assertThat(provider.getBooleanEvaluation(key, !bool, context).getValue())
                        .as(where)
                        .isEqualTo(enabled ? bool : !bool);
                break;
            case STRING:
                String string = expected.textValue();
                String stringFallback = string + "-fallback";
                assertThat(provider.getStringEvaluation(key, stringFallback, context)
                                .getValue())
                        .as(where)
                        .isEqualTo(enabled ? string : stringFallback);
                break;
            case NUMBER:
                assertNumberResolves(provider, context, key, where, expected, enabled);
                break;
            case OBJECT:
            case ARRAY:
                Value structure = Value.objectToValue(MAPPER.convertValue(expected, Object.class));
                Value objectFallback = new Value("fallback");
                assertThat(provider.getObjectEvaluation(key, objectFallback, context)
                                .getValue())
                        .as(where)
                        .isEqualTo(enabled ? structure : objectFallback);
                break;
            default:
                fail("%s is a %s, which is not a flag value", where, expected.getNodeType());
                break;
        }
    }

    /**
     * Resolves a numeric flag through the accessor its literal calls for.
     *
     * <p>Which accessor that is <em>is</em> the assertion. An integral literal goes through the
     * integer accessor and a fractional one through the float accessor, and the SDK's provider
     * refuses a variant of the other type, so this is where a decoder that widened or narrowed a
     * number fails. 2^53 − 1 has no room in an {@link Integer} and goes through the long accessor,
     * which is also the reason a Java provider leaves {@code @large-integers} undeclared.
     *
     * <p>The accessor is still chosen by the literal for a disabled flag, so a {@code disabled-*}
     * flag whose type was mangled by the decoder is caught the same way: the answer is then neither
     * the configured value nor the fallback.
     */
    private static void assertNumberResolves(
            InMemoryProvider provider,
            ImmutableContext context,
            String key,
            String where,
            JsonNode expected,
            boolean enabled) {

        if (!expected.isIntegralNumber()) {
            double value = expected.doubleValue();
            assertThat(provider.getDoubleEvaluation(key, value + 1, context).getValue())
                    .as(where)
                    .isEqualTo(enabled ? value : value + 1);
        } else if (expected.canConvertToInt()) {
            int value = expected.intValue();
            assertThat(provider.getIntegerEvaluation(key, value + 1, context).getValue())
                    .as(where)
                    .isEqualTo(enabled ? value : value + 1);
        } else {
            long value = expected.longValue();
            assertThat(provider.getLongEvaluation(key, value + 1, context).getValue())
                    .as(where)
                    .isEqualTo(enabled ? value : value + 1);
        }
    }
}
