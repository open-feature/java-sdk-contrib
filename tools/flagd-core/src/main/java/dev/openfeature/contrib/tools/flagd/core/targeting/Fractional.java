package dev.openfeature.contrib.tools.flagd.core.targeting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.openfeature.contrib.tools.flagd.core.cbor.CborEncoder;
import io.github.jamsesso.jsonlogic.JsonLogicException;
import io.github.jamsesso.jsonlogic.evaluator.JsonLogicEvaluationException;
import io.github.jamsesso.jsonlogic.evaluator.expressions.PreEvaluatedArgumentsExpression;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.MurmurHash3;

/**
 * Fractional targeting operation for bucket-based flag distribution.
 */
@Slf4j
class Fractional implements PreEvaluatedArgumentsExpression {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final JsonNodeFactory NODE_FACTORY = JsonNodeFactory.instance;
    static final int MAX_WEIGHT = Integer.MAX_VALUE;

    @Override
    public String key() {
        return "fractional";
    }

    @Override
    @SuppressWarnings("unchecked") // json-logic-java's PreEvaluatedArgumentsExpression uses raw List
    public Object evaluate(List arguments, Object data, String jsonPath) throws JsonLogicEvaluationException {
        if (arguments.size() < 1) {
            return null;
        }

        final Operator.FlagProperties properties = new Operator.FlagProperties(data);

        final Object bucketBy;
        final List<Object> distributions;

        // json-logic pre-evaluation flattens a single-entry fractional
        // e.g. [["single",1]] becomes ["single", 1]; detect and re-wrap
        if (isFlattened(arguments)) {
            if (properties.getTargetingKey() == null) {
                log.debug("Missing fallback targeting key");
                return null;
            }
            bucketBy = java.util.Arrays.asList(properties.getFlagKey(), properties.getTargetingKey());
            distributions = List.of(arguments);
        } else if (arguments.get(0) instanceof String
                || arguments.get(0) instanceof Boolean
                || arguments.get(0) instanceof Number
                || arguments.get(0) instanceof java.util.Map) {
            // first arg is a primitive or Map, use for bucketing
            bucketBy = arguments.get(0);
            distributions = arguments.subList(1, arguments.size());
        } else {
            // fallback to targeting key if present
            if (properties.getTargetingKey() == null) {
                log.debug("Missing fallback targeting key");
                return null;
            }

            bucketBy = java.util.Arrays.asList(properties.getFlagKey(), properties.getTargetingKey());

            if (arguments.get(0) == null) {
                // arguments.get(0) resolved to null, skip it in distributions
                distributions = arguments.subList(1, arguments.size());
            } else {
                distributions = arguments;
            }
        }

        final List<FractionProperty> propertyList = new ArrayList<>();
        long totalWeight = 0;

        for (Object dist : distributions) {
            try {
                FractionProperty fractionProperty = new FractionProperty(dist, jsonPath);
                propertyList.add(fractionProperty);
                totalWeight += fractionProperty.getWeight();
            } catch (JsonLogicException e) {
                if ("Property is not an array".equals(e.getMessage())) {
                    throw new JsonLogicEvaluationException(
                            "Error parsing fractional targeting rule: " + e.getMessage(), jsonPath);
                }
                return null;
            }
        }

        if (totalWeight > MAX_WEIGHT) {
            log.debug("Total weight {} exceeds maximum allowed value {}", totalWeight, MAX_WEIGHT);
            return null;
        }

        if (totalWeight == 0) {
            log.debug("Total weight is 0, no valid distribution possible");
            return null;
        }

        // find distribution
        return distributeValue(bucketBy, propertyList, (int) totalWeight, jsonPath);
    }

    private static Object distributeValue(
            final Object hashKey,
            final List<FractionProperty> propertyList,
            final int totalWeight,
            final String jsonPath)
            throws JsonLogicEvaluationException {
        byte[] bytes;
        try {
            JsonNode node = normalizeNumbers(OBJECT_MAPPER.valueToTree(hashKey));
            bytes = CborEncoder.encode(node);
        } catch (Exception e) {
            log.debug("Error converting hashKey to CBOR", e);
            throw new JsonLogicEvaluationException("Error converting hashKey to CBOR", jsonPath);
        }
        int mmrHash = MurmurHash3.hash32x86(bytes, 0, bytes.length, 0);
        return distributeValueFromHash(mmrHash, propertyList, totalWeight, jsonPath);
    }

    // normalize for hashing parity (ADR): whole doubles -> int, -0.0 -> 0, else unchanged
    private static JsonNode normalizeNumbers(JsonNode node) {
        if (node.isObject()) {
            ObjectNode result = NODE_FACTORY.objectNode();
            node.fields().forEachRemaining(field -> result.set(field.getKey(), normalizeNumbers(field.getValue())));
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = NODE_FACTORY.arrayNode();
            node.forEach(child -> result.add(normalizeNumbers(child)));
            return result;
        }
        if (node.isFloatingPointNumber()) {
            double value = node.asDouble();
            if (!Double.isInfinite(value)
                    && value == Math.floor(value)
                    && value >= Long.MIN_VALUE
                    && value <= Long.MAX_VALUE) {
                return NODE_FACTORY.numberNode((long) value);
            }
        }
        return node;
    }

    /**
     * Checks if arguments have been flattened by json-logic pre-evaluation.
     * A flattened list contains no List elements (e.g. ["single", 1] instead of [["single", 1]]).
     */
    private static boolean isFlattened(List<?> arguments) {
        for (Object arg : arguments) {
            if (arg instanceof List) {
                return false;
            }
        }
        return true;
    }

    static Object distributeValueFromHash(
            final int hash, final List<FractionProperty> propertyList, final int totalWeight, final String jsonPath)
            throws JsonLogicEvaluationException {
        long longHash = Integer.toUnsignedLong(hash);
        int bucket = (int) ((longHash * totalWeight) >>> 32);

        int bucketSum = 0;
        for (FractionProperty p : propertyList) {
            bucketSum += p.weight;

            if (bucket < bucketSum) {
                return p.getVariant();
            }
        }

        // this shall not be reached
        throw new JsonLogicEvaluationException("Unable to find a correct bucket for hash " + hash, jsonPath);
    }

    @Getter
    @SuppressWarnings({"checkstyle:NoFinalizer"})
    static class FractionProperty {
        private final Object variant;
        private final int weight;

        protected final void finalize() {
            // DO NOT REMOVE, spotbugs: CT_CONSTRUCTOR_THROW
        }

        FractionProperty(final Object from, String jsonPath) throws JsonLogicException {
            if (!(from instanceof List<?>)) {
                throw new JsonLogicException("Property is not an array", jsonPath);
            }

            final List<?> array = (List) from;

            if (array.isEmpty()) {
                throw new JsonLogicException("Fraction property needs at least one element", jsonPath);
            }

            // variant must be a primitive (string, number, boolean) or null;
            // nested JSONLogic expressions are pre-evaluated to these types
            Object first = array.get(0);
            if (first instanceof String || first instanceof Number || first instanceof Boolean || first == null) {
                variant = first;
            } else {
                throw new JsonLogicException(
                        "First element of the fraction property must resolve to a string, number, boolean, or null",
                        jsonPath);
            }

            if (array.size() >= 2) {
                // weight must be a number
                if (!(array.get(1) instanceof Number)) {
                    throw new JsonLogicException("Second element of the fraction property is not a number", jsonPath);
                }
                Number rawWeight = (Number) array.get(1);

                // weights must be integers
                double weightDouble = rawWeight.doubleValue();
                if (Double.isInfinite(weightDouble)
                        || Double.isNaN(weightDouble)
                        || weightDouble != Math.floor(weightDouble)) {
                    throw new JsonLogicException("Weights must be integers", jsonPath);
                }

                // negative weights can be the result of rollout calculations,
                // so we clamp to 0 rather than throwing an error
                weight = Math.max(0, (int) weightDouble);
            } else {
                weight = 1;
            }
        }
    }
}
