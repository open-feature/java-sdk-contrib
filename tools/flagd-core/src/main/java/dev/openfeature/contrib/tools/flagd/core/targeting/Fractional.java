package dev.openfeature.contrib.tools.flagd.core.targeting;

import io.github.jamsesso.jsonlogic.JsonLogicException;
import io.github.jamsesso.jsonlogic.evaluator.JsonLogicEvaluationException;
import io.github.jamsesso.jsonlogic.evaluator.expressions.PreEvaluatedArgumentsExpression;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
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
    public Object evaluate(List arguments, Object data, String jsonPath) throws JsonLogicEvaluationException {
        if (arguments.size() < 1) {
            return null;
        }

        final Operator.FlagProperties properties = new Operator.FlagProperties(data);

        // check optional string target in first arg
        Object arg1 = arguments.get(0);

        final String bucketBy;
        final Object[] distributions;

        if (arg1 instanceof String) {
            // first arg is a String, use for bucketing
            bucketBy = (String) arg1;
            Object[] source = arguments.toArray();
            distributions = Arrays.copyOfRange(source, 1, source.length);
        } else {
            // fallback to targeting key if present
            if (properties.getTargetingKey() == null) {
                log.debug("Missing fallback targeting key");
                return null;
            }

            bucketBy = properties.getFlagKey() + properties.getTargetingKey();
            distributions = arguments.toArray();
        }

        final List<FractionProperty> propertyList = new ArrayList<>();
        long totalWeight = 0;

        try {
            for (Object dist : distributions) {
                FractionProperty fractionProperty = new FractionProperty(dist, jsonPath);
                propertyList.add(fractionProperty);
                totalWeight += fractionProperty.getWeight();
            }
        } catch (JsonLogicException e) {
            log.debug("Error parsing fractional targeting rule", e);
            return null;
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
            final String hashKey,
            final List<FractionProperty> propertyList,
            final int totalWeight,
            final String jsonPath)
            throws JsonLogicEvaluationException {
        byte[] bytes = hashKey.getBytes(StandardCharsets.UTF_8);
        int mmrHash = MurmurHash3.hash32x86(bytes, 0, bytes.length, 0);
        return distributeValueFromHash(mmrHash, propertyList, totalWeight, jsonPath);
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

                // clamp negative weights to 0
                weight = Math.max(0, (int) weightDouble);
            } else {
                weight = 1;
            }
        }
    }
}
