package dev.openfeature.contrib.tools.flagd.core.targeting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.upokecenter.cbor.CBORObject;
import io.github.jamsesso.jsonlogic.JsonLogicException;
import io.github.jamsesso.jsonlogic.evaluator.JsonLogicEvaluationException;
import io.github.jamsesso.jsonlogic.evaluator.expressions.PreEvaluatedArgumentsExpression;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.MurmurHash3;

/**
 * Fractional targeting operation for bucket-based flag distribution.
 */
@Slf4j
class Fractional implements PreEvaluatedArgumentsExpression {

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
                if (arguments.size() == 2) {
                    throw new dev.openfeature.sdk.exceptions.GeneralError("Missing fallback targeting key");
                }
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
                    throw new io.github.jamsesso.jsonlogic.evaluator.JsonLogicEvaluationException(
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
            JsonNode node = objectMapper.valueToTree(hashKey);
            CBORObject dataItem = convertNode(node);
            bytes = dataItem.EncodeToBytes();
        } catch (Exception e) {
            log.debug("Error converting hashKey to CBOR", e);
            throw new JsonLogicEvaluationException("Error converting hashKey to CBOR", jsonPath);
        }
        int mmrHash = MurmurHash3.hash32x86(bytes, 0, bytes.length, 0);
        return distributeValueFromHash(mmrHash, propertyList, totalWeight, jsonPath);
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

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static class CanonicalKeyComparator implements Comparator<String>, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public int compare(String k1, String k2) {
            byte[] b1 = k1.getBytes(StandardCharsets.UTF_8);
            byte[] b2 = k2.getBytes(StandardCharsets.UTF_8);
            if (b1.length != b2.length) {
                return Integer.compare(b1.length, b2.length);
            }
            for (int i = 0; i < b1.length; i++) {
                int v1 = b1[i] & 0xFF;
                int v2 = b2[i] & 0xFF;
                if (v1 != v2) {
                    return Integer.compare(v1, v2);
                }
            }
            return 0;
        }
    }

    private static final CanonicalKeyComparator KEY_COMPARATOR = new CanonicalKeyComparator();

    private static CBORObject convertNode(JsonNode node) {
        if (node.isNull()) {
            return CBORObject.Null;
        } else if (node.isBoolean()) {
            return CBORObject.FromObject(node.asBoolean());
        } else if (node.isTextual()) {
            return CBORObject.FromObject(node.asText());
        } else if (node.isNumber()) {
            if (node.isIntegralNumber()) {
                return CBORObject.FromObject(node.asLong());
            } else {
                double val = node.asDouble();
                if (val == Math.floor(val) && val >= Long.MIN_VALUE && val <= Long.MAX_VALUE) {
                    return CBORObject.FromObject((long) val);
                }
                return CBORObject.FromObject(val);
            }
        } else if (node.isArray()) {
            CBORObject array = CBORObject.NewArray();
            for (JsonNode item : node) {
                array.Add(convertNode(item));
            }
            return array;
        } else if (node.isObject()) {
            CBORObject map = CBORObject.NewOrderedMap();
            List<String> fieldNames = new ArrayList<>();
            node.fieldNames().forEachRemaining(fieldNames::add);
            Collections.sort(fieldNames, KEY_COMPARATOR);
            for (String fieldName : fieldNames) {
                map.Add(fieldName, convertNode(node.get(fieldName)));
            }
            return map;
        }
        throw new IllegalArgumentException("Unsupported node type: " + node.getNodeType());
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
