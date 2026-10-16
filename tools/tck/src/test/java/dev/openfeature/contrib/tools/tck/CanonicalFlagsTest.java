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
 * Pins the in-process flag set to the packaged definition, reading the JSON independently of the
 * decoder and resolving each flag through the accessor its JSON type calls for.
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
        assertThat(packaged).as("the packaged definition defines flags").isNotEmpty();
    }

    @Test
    @DisplayName("the decoded flag set defines exactly the keys the packaged definition defines")
    void theDecodedSetHasExactlyThePackagedKeys() {
        assertThat(CanonicalFlags.flagSet().keySet())
                .as("decoded keys match packaged keys")
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

        assertThat(disabled).as("at least one disabled flag was checked").isNotEmpty();
    }

    /** Resolves one flag via its JSON type's accessor; the fallback never equals the expected value. */
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

    /** Resolves a numeric flag via the int, long or double accessor its literal calls for. */
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
