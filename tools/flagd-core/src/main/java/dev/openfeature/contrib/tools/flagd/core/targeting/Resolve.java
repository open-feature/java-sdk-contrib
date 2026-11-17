package dev.openfeature.contrib.tools.flagd.core.targeting;

import io.github.jamsesso.jsonlogic.evaluator.expressions.PreEvaluatedArgumentsExpression;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Terminal {@code resolve} operation: {@code ["variant", "reason"?, [["key", value], ...]?]}.
 * Selects a variant and optionally overrides the reason and adds rule metadata. Placement is enforced by the
 * targeting schema; malformed computed arguments return null, so the flag falls back to its default variant.
 */
@Slf4j
class Resolve implements PreEvaluatedArgumentsExpression {

    @Override
    public String key() {
        return "resolve";
    }

    @Override
    public Object evaluate(List arguments, Object data, String jsonPath) {
        if (arguments.isEmpty() || arguments.size() > 3) {
            log.debug("resolve takes 1 to 3 arguments");
            return null;
        }

        // coerce like a plain targeting result
        final Object variant = arguments.get(0);
        if (!(variant instanceof String || variant instanceof Boolean || variant instanceof Number)) {
            log.debug("resolve variant must be a string, boolean, or number");
            return null;
        }

        final Object reason = arguments.size() > 1 ? arguments.get(1) : null;
        if (reason != null && !(reason instanceof String)) {
            log.debug("resolve reason must be a string or null");
            return null;
        }

        final Map<String, Object> metadata = new HashMap<>();
        final Object pairs = arguments.size() > 2 ? arguments.get(2) : null;
        if (pairs != null && !addMetadata(pairs, metadata)) {
            return null;
        }

        return new ResolveResult(variant.toString(), (String) reason, metadata);
    }

    private static boolean addMetadata(Object pairs, Map<String, Object> metadata) {
        if (!(pairs instanceof List)) {
            log.debug("resolve metadata must be an array of [key, value] pairs");
            return false;
        }
        for (Object pair : (List<?>) pairs) {
            final List<?> entry = pair instanceof List ? (List<?>) pair : List.of();
            if (entry.size() != 2 || !(entry.get(0) instanceof String)) {
                log.debug("resolve metadata pair must be a [string, value] array");
                return false;
            }
            final Object value = entry.get(1);
            final Object metadataValue = value instanceof Number ? metadataNumber((Number) value) : value;
            if (!(metadataValue instanceof String
                    || metadataValue instanceof Boolean
                    || metadataValue instanceof Number)) {
                log.debug("resolve metadata value must be a string, finite number, or boolean");
                return false;
            }
            metadata.put((String) entry.get(0), metadataValue);
        }
        return true;
    }

    /**
     * JsonLogic yields doubles, so whole numbers become ints like they are in flag metadata.
     * NaN and Infinity (e.g. a computed 1/0) can't be encoded as JSON, so they return null.
     */
    private static Object metadataNumber(Number value) {
        final double number = value.doubleValue();
        if (!Double.isFinite(number)) {
            return null;
        }
        final boolean wholeInt =
                number == Math.rint(number) && number >= Integer.MIN_VALUE && number <= Integer.MAX_VALUE;
        return wholeInt ? (Object) (int) number : number;
    }
}
