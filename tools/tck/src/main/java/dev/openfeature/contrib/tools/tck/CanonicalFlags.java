package dev.openfeature.contrib.tools.tck;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.providers.memory.Flag;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The canonical flag set, decoded from {@code flags/canonical-flags.json} (copied from the spec
 * submodule and packaged on the classpath).
 *
 * <p>Decoding preserves what the scenarios test: {@code missing-flag} stays absent, no flag gets a
 * context evaluator, and numbers keep their written kind and width (see {@link #number}).
 */
final class CanonicalFlags {

    /** Classpath location of the canonical flag definition. */
    static final String RESOURCE = "flags/canonical-flags.json";

    private static final String COMMENT = "$comment";

    private static final String ENABLED = "ENABLED";
    private static final String DISABLED = "DISABLED";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CanonicalFlags() {}

    /**
     * Reads the packaged canonical flag definition.
     *
     * @return the raw JSON bytes
     * @throws IllegalStateException if the definition is not on the classpath
     */
    static byte[] definition() {
        ClassLoader loader = CanonicalFlags.class.getClassLoader();
        try (InputStream in = loader.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("The canonical flag definition " + RESOURCE + " is not on the "
                        + "classpath; check out the spec submodule and run 'mvn generate-resources' on tools/tck.");
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the canonical flag definition " + RESOURCE, e);
        }
    }

    /**
     * Decodes the packaged canonical flag definition into {@code InMemoryProvider} flags.
     *
     * @return the canonical flag set, in file order, unmodifiable
     * @throws IllegalStateException if the definition is missing or malformed
     */
    static Map<String, Flag<?>> flagSet() {
        return decode(definition());
    }

    /**
     * Decodes a canonical flag definition.
     *
     * @param json the definition, in flagd's flag-definition format
     * @return the flag set, in document order, unmodifiable
     * @throws IllegalStateException if the document is not the expected shape
     */
    static Map<String, Flag<?>> decode(byte[] json) {
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (IOException e) {
            throw new IllegalStateException("The canonical flag definition " + RESOURCE + " is not valid JSON", e);
        }

        JsonNode flags = root.path("flags");
        if (!flags.isObject()) {
            throw new IllegalStateException("The canonical flag definition " + RESOURCE
                    + " has no 'flags' object. The format is {\"flags\": {\"<key>\": {...}}}.");
        }

        Map<String, Flag<?>> decoded = new LinkedHashMap<>();
        for (Iterator<Map.Entry<String, JsonNode>> it = flags.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> entry = it.next();
            if (COMMENT.equals(entry.getKey())) {
                continue;
            }
            decoded.put(entry.getKey(), flag(entry.getKey(), entry.getValue()));
        }
        if (decoded.isEmpty()) {
            throw new IllegalStateException("The canonical flag definition " + RESOURCE + " defines no flags.");
        }
        return Collections.unmodifiableMap(decoded);
    }

    private static Flag<?> flag(String key, JsonNode definition) {
        String state = definition.path("state").asText(null);
        if (!ENABLED.equals(state) && !DISABLED.equals(state)) {
            throw new IllegalStateException("Canonical flag '" + key + "' has state '" + state + "', which is neither "
                    + ENABLED + " nor " + DISABLED + ".");
        }

        String defaultVariant = definition.path("defaultVariant").asText(null);
        if (defaultVariant == null) {
            throw new IllegalStateException("Canonical flag '" + key + "' names no defaultVariant.");
        }

        JsonNode variants = definition.path("variants");
        if (!variants.isObject()) {
            throw new IllegalStateException("Canonical flag '" + key + "' has no 'variants' object.");
        }

        Map<String, Object> values = new LinkedHashMap<>();
        for (Iterator<Map.Entry<String, JsonNode>> it = variants.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> variant = it.next();
            if (COMMENT.equals(variant.getKey())) {
                continue;
            }
            values.put(variant.getKey(), value(key, variant.getKey(), variant.getValue()));
        }
        if (!values.containsKey(defaultVariant)) {
            throw new IllegalStateException("Canonical flag '" + key + "' resolves to variant '" + defaultVariant
                    + "', which it does not define. Its variants are " + values.keySet() + ".");
        }

        return Flag.builder()
                .variants(values)
                .defaultVariant(defaultVariant)
                .disabled(DISABLED.equals(state))
                .build();
    }

    private static Object value(String key, String variant, JsonNode node) {
        switch (node.getNodeType()) {
            case BOOLEAN:
                return node.booleanValue();
            case STRING:
                return node.textValue();
            case NUMBER:
                return number(key, variant, node);
            case OBJECT:
            case ARRAY:
                // same conversion as feature-file object values, see TckValues
                return Value.objectToValue(MAPPER.convertValue(node, Object.class));
            case NULL:
                return null;
            default:
                throw new IllegalStateException("Canonical flag '" + key + "', variant '" + variant + "' is a "
                        + node.getNodeType() + ", which is not a flag value.");
        }
    }

    /**
     * Decodes a number as written: fractional to {@link Double}, integral to {@link Integer} or, if
     * too wide, {@link Long}. {@code InMemoryProvider} matches by type, so {@code 10.0} must not
     * become {@code 10}.
     */
    private static Object number(String key, String variant, JsonNode node) {
        if (!node.isIntegralNumber()) {
            return node.doubleValue();
        }
        if (node.canConvertToInt()) {
            return node.intValue();
        }
        if (node.canConvertToLong()) {
            return node.longValue();
        }
        throw new IllegalStateException("Canonical flag '" + key + "', variant '" + variant + "' is " + node.asText()
                + ", which does not fit a Long.");
    }
}
