package dev.openfeature.contrib.providers.flagd.resolver.process.targeting;

import io.github.jamsesso.jsonlogic.JsonLogicException;
import io.github.jamsesso.jsonlogic.evaluator.JsonLogicEvaluationException;
import io.github.jamsesso.jsonlogic.evaluator.expressions.PreEvaluatedArgumentsExpression;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.MurmurHash3;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
class Fractional implements PreEvaluatedArgumentsExpression {

    public String key() {
        return "fractional";
    }

    public Object evaluate(List arguments, Object data) throws JsonLogicEvaluationException {
        if (arguments.size() < 2) {
            return null;
        }

        final Operator.FlagProperties properties = new Operator.FlagProperties(data);

        // check optional string target in first arg
        Object arg1 = arguments.get(0);

        final String bucketBy;
        final Object[] distibutions;

        if (arg1 instanceof String) {
            // first arg is a String, use for bucketing
            bucketBy = (String) arg1;

            Object[] source = arguments.toArray();
            distibutions = Arrays.copyOfRange(source, 1, source.length);
        } else {
            // fallback to targeting key if present
            if (properties.getTargetingKey() == null) {
                log.debug("Missing fallback targeting key");
                return null;
            }

            bucketBy = properties.getTargetingKey();
            distibutions = arguments.toArray();
        }

        final String hashKey = properties.getFlagKey() + bucketBy;
        final List<FractionProperty> propertyList = new ArrayList<>();

        double distribution = 0;
        try {
            for (Object dist : distibutions) {
                FractionProperty fractionProperty = new FractionProperty(dist);
                propertyList.add(fractionProperty);
                distribution += fractionProperty.getPercentage();
            }
        } catch (JsonLogicException e) {
            log.debug("Error parsing fractional targeting rule", e);
            return null;
        }

        if (distribution != 100) {
            log.debug("Fractional properties do not sum to 100");
            return null;
        }

        // find distribution
        return distributeValue(hashKey, propertyList);
    }

    private static String distributeValue(final String hashKey, final List<FractionProperty> propertyList)
            throws JsonLogicEvaluationException {
        byte[] bytes = hashKey.getBytes(StandardCharsets.UTF_8);
        int mmrHash = MurmurHash3.hash32x86(bytes, 0, bytes.length, 0);
        int bucket = (int) ((Math.abs(mmrHash) * 1.0f / Integer.MAX_VALUE) * 100);

        int bucketSum = 0;
        for (FractionProperty p : propertyList) {
            bucketSum += p.getPercentage();

            if (bucket < bucketSum) {
                return p.getVariant();
            }
        }

        // this shall not be reached
        throw new JsonLogicEvaluationException("Unable to find a correct bucket");
    }

    @Getter
    private static class FractionProperty {
        private final String variant;
        private final int percentage;

        FractionProperty(final Object from) throws JsonLogicException {
            if (!(from instanceof List<?>)) {
                throw new JsonLogicException("Property is not an array");
            }

            final List<?> array = (List) from;

            if (array.size() != 2) {
                throw new JsonLogicException("Fraction property does not have two elements");
            }

            // first must be a string
            if (!(array.get(0) instanceof String)) {
                throw new JsonLogicException("First element of the fraction property is not a string variant");
            }

            // second element must be a number
            if (!(array.get(1) instanceof Number)) {
                throw new JsonLogicException("Second element of the fraction property is not a number");
            }

            variant = (String) array.get(0);
            percentage = ((Number) array.get(1)).intValue();
        }

    }
}
