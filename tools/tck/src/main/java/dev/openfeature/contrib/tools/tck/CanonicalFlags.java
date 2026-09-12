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
 * The canonical flag set, decoded from the definition this artifact packages.
 *
 * <p>{@code flags/canonical-flags.json} is one of the three language-agnostic conformance artifacts.
 * It is not owned here: it lives in open-feature/spec under
 * {@code specification/assets/provider-tck/}, is copied in from the {@code spec} submodule at build
 * time and is packaged into the release JAR, which is why this class reads it off the classpath.
 *
 * <p><strong>Why decoded rather than transcribed.</strong> Appendix F exposes the canonical set so
 * that an adopting provider can seed a backend directly from the canonical definition rather than
 * transcribing it, transcription being the usual way the two drift apart. A hand-written copy inside
 * the TCK is the same drift with a shorter fuse: the in-process self-tests would then verify the
 * suite against a second baseline of our own, so a rename in the spec makes them pass against the
 * wrong flags while reporting green. Go's TCK decodes the same file for the same reason, and this
 * follows it.
 *
 * <p>The file is flagd's flag-definition format —
 * <code>{"flags": {"&lt;key&gt;": {"state", "variants", "defaultVariant"}}}</code> — because that is
 * the only widely implemented vendor-neutral format today. {@code $comment} members are
 * documentation and are ignored wherever they appear.
 *
 * <h2>What the decoding has to preserve</h2>
 *
 * <p>A loader that "cleans up" values destroys exactly what the scenarios test, so three properties
 * of the file survive it deliberately:
 *
 * <ul>
 *   <li>{@code missing-flag} is absent, which is what the {@code FLAG_NOT_FOUND} scenario tests.
 *       Nothing here adds flags the file does not define.
 *   <li>no flag carries a {@link dev.openfeature.sdk.providers.memory.ContextEvaluator}, so every
 *       evaluation reports reason {@code STATIC} as the feature files expect. The TCK tests a
 *       provider's mapping of a response, not a backend's evaluation logic.
 *   <li>a number keeps the width and the kind it was written with. {@code 10} becomes an
 *       {@link Integer} and {@code 10.0} a {@link Double}, because
 *       {@link dev.openfeature.sdk.providers.memory.InMemoryProvider} matches a variant by type: an
 *       {@code integral-float-flag} decoded as the integer {@code 10} would let the
 *       lossless-coercion scenario pass without anything being coerced. {@code 2147483647} fits an
 *       {@link Integer} and stays one, so {@code getIntegerDetails} can ask for it; 2^53 − 1 does
 *       not and becomes a {@link Long}. See {@link #number}.
 * </ul>
 */
final class CanonicalFlags {

    /**
     * Classpath location of the canonical flag definition, packaged by the {@code
     * copy-provider-tck-flags} execution in this module's POM.
     */
    static final String RESOURCE = "flags/canonical-flags.json";

    /** Documentation member, ignored wherever it appears. */
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
                        + "classpath. It is copied in from the spec submodule by the copy-provider-tck-flags "
                        + "execution and packaged into this artifact, so a run without it is a build problem "
                        + "rather than a provider defect: run 'mvn generate-resources' on tools/tck, "
                        + "having checked the spec submodule out.");
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the canonical flag definition " + RESOURCE, e);
        }
    }

    /**
     * Decodes the packaged canonical flag definition into {@code InMemoryProvider} flags.
     *
     * @return the canonical flag set, in the order the file defines it, unmodifiable
     * @throws IllegalStateException if the definition is missing or is not the shape this decoder
     *     expects, which for a pinned spec revision means the pin moved under it
     */
    static Map<String, Flag<?>> flagSet() {
        return decode(definition());
    }

    /**
     * Decodes a canonical flag definition.
     *
     * @param json the definition, in flagd's flag-definition format
     * @return the flag set, in the order the document defines it, unmodifiable
     * @throws IllegalStateException if the document is not the shape this decoder expects
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
            throw new IllegalStateException(
                    "The canonical flag definition " + RESOURCE + " defines no flags, so there is nothing to seed.");
        }
        return Collections.unmodifiableMap(decoded);
    }

    /** Decodes one flag definition. */
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

    /** Converts one variant value to what {@code InMemoryProvider}'s type matching expects. */
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
                // The same conversion the feature files go through for an Object value, so a seeded
                // structure and an expected one are comparable: see TckValues.
                return Value.objectToValue(MAPPER.convertValue(node, Object.class));
            case NULL:
                return null;
            default:
                throw new IllegalStateException("Canonical flag '" + key + "', variant '" + variant + "' is a "
                        + node.getNodeType() + ", which is not a flag value.");
        }
    }

    /**
     * Splits a JSON number on how it was written, and on whether it fits.
     *
     * <p>This is the load-bearing half of the decoding. A literal with a fraction or an exponent is a
     * {@link Double} and an integral one is an {@link Integer} or, where 32 bits have no room for it,
     * a {@link Long}. {@code InMemoryProvider} matches a variant against the requested type, so it is
     * the decoded type that decides whether {@code integer-flag} is an integer flag and
     * {@code integral-float-flag} a float one — and the file's own comment warns that a loader which
     * turns {@code 10.0} back into {@code 10} lets the lossless-coercion scenario pass without
     * coercing anything.
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
                + ", which does not fit a Long. No canonical value exceeds 2^53 - 1.");
    }
}
