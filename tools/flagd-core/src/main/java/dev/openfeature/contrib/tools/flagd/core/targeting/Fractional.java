package dev.openfeature.contrib.tools.flagd.core.targeting;

import io.github.jamsesso.jsonlogic.JsonLogicException;
import io.github.jamsesso.jsonlogic.evaluator.JsonLogicEvaluationException;
import io.github.jamsesso.jsonlogic.evaluator.expressions.PreEvaluatedArgumentsExpression;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.MurmurHash3;

/**
 * Fractional targeting operation for bucket-based flag distribution.
 */
@Slf4j
class Fractional implements PreEvaluatedArgumentsExpression {

    @Override
    public String key() {
        return "fractional";
    }

    @Override
    public Object evaluate(List arguments, Object data, String jsonPath) throws JsonLogicEvaluationException {
        if (arguments.size() < 2) {
            return null;
        }

        final Operator.FlagProperties properties = new Operator.FlagProperties(data);

        // check optional string target in first arg
        Object arg1 = arguments.get(0);

        final byte[] bucketBy;
        final Object[] distributions;

        if (arg1 instanceof String) {
            bucketBy = ((String) arg1).getBytes(StandardCharsets.UTF_8);
            Object[] source = arguments.toArray();
            distributions = Arrays.copyOfRange(source, 1, source.length);
        } else if (arg1 instanceof Number) {
            bucketBy = numberToByteArray((Number) arg1);
            Object[] source = arguments.toArray();
            distributions = Arrays.copyOfRange(source, 1, source.length);
        } else if (arg1 instanceof Boolean) {
            bucketBy = new byte[] {(byte) (((boolean) arg1) ? 1 : 0)};
            Object[] source = arguments.toArray();
            distributions = Arrays.copyOfRange(source, 1, source.length);
        } else {
            // fallback to targeting key if present
            if (properties.getTargetingKey() == null) {
                log.debug("Missing fallback targeting key");
                return null;
            }

            bucketBy = (properties.getFlagKey() + properties.getTargetingKey()).getBytes(StandardCharsets.UTF_8);
            distributions = arguments.toArray();
        }

        final List<FractionProperty> propertyList = new ArrayList<>();
        int totalWeight = 0;

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

        // find distribution
        return distributeValue(bucketBy, propertyList, totalWeight, jsonPath);
    }

    private byte[] numberToByteArray(Number number) {
        if (number instanceof Integer) {
            return new byte[] {
                (byte) ((int) number >> 24),
                (byte) ((int) number >> 16),
                (byte) ((int) number >> 8),
                (byte) ((int) number)
            };
        } else if (number instanceof Double) {
            return numberToByteArray(Double.doubleToLongBits((Double) number));
        } else if (number instanceof Long) {
            return new byte[] {
                (byte) ((long) number >> 56),
                (byte) ((long) number >> 48),
                (byte) ((long) number >> 40),
                (byte) ((long) number >> 32),
                (byte) ((long) number >> 24),
                (byte) ((long) number >> 16),
                (byte) ((long) number >> 8),
                (byte) ((long) number)
            };
        } else if (number instanceof BigInteger) {
            return ((BigInteger) number).toByteArray();
        } else if (number instanceof Byte) {
            return new byte[] {(byte) number};
        } else if (number instanceof Short) {
            return new byte[] {
                (byte) ((short) number >> 8),
                (byte) ((short) number)
            };
        } else if (number instanceof Float) {
            return numberToByteArray(Float.floatToIntBits((Float) number));
        } else if (number instanceof BigDecimal) {
            return numberToByteArray(Double.doubleToLongBits(number.doubleValue()));
        } else {
            throw new IllegalArgumentException("Unsupported number type: " + number.getClass());
        }
    }

    private static String distributeValue(
            final byte[] hashKey, final List<FractionProperty> propertyList, final int totalWeight,
            final String jsonPath)
            throws JsonLogicEvaluationException {
        int mmrHash = MurmurHash3.hash32x86(hashKey, 0, hashKey.length, 0);
        int bucket = Math.abs((int) ((mmrHash * (long) totalWeight) >> 32));

        int bucketSum = 0;
        for (FractionProperty p : propertyList) {
            bucketSum += p.weight;

            if (bucket < bucketSum) {
                return p.getVariant();
            }
        }

        // this shall not be reached
        throw new JsonLogicEvaluationException("Unable to find a correct bucket", jsonPath);
    }

    @Getter
    @SuppressWarnings({"checkstyle:NoFinalizer"})
    private static class FractionProperty {
        private final String variant;
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

            // first must be a string
            if (!(array.get(0) instanceof String)) {
                throw new JsonLogicException(
                        "First element of the fraction property is not a string variant", jsonPath);
            }

            variant = (String) array.get(0);
            if (array.size() >= 2) {
                // second element must be a number
                if (!(array.get(1) instanceof Number)) {
                    throw new JsonLogicException("Second element of the fraction property is not a number",
                            jsonPath);
                }
                weight = ((Number) array.get(1)).intValue();
            } else {
                weight = 1;
            }
        }

        float getPercentage(int totalWeight) {
            if (weight == 0) {
                return 0;
            }
            return (float) (weight * 100) / totalWeight;
        }
    }
}
