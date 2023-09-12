package dev.openfeature.contrib.providers.flagd.resolver.process.targeting;

import dev.openfeature.sdk.EvaluationContext;
import io.github.jamsesso.jsonlogic.JsonLogic;
import io.github.jamsesso.jsonlogic.JsonLogicException;
import lombok.Getter;

import java.util.Map;

/**
 * Targeting operator wraps JsonLogic handlers and expose a simple API for external layers.
 * This helps to isolate external dependencies to this package.
 */
public class Operator {

    final static String FLAG_KEY = "$flagKey";
    final static String TARGET_KEY = "targetingKey";

    private final JsonLogic jsonLogicHandler;

    /**
     * Construct a targeting operator.
     */
    public Operator() {
        jsonLogicHandler = new JsonLogic();
        jsonLogicHandler.addOperation(new Fractional());
        jsonLogicHandler.addOperation(new SemVer());
        jsonLogicHandler.addOperation(new StringComp(StringComp.Type.STARTS_WITH));
        jsonLogicHandler.addOperation(new StringComp(StringComp.Type.ENDS_WITH));
    }

    public Object apply(final String flagKey, final String targetingRule, final EvaluationContext ctx)
            throws TargetingRuleException {
        final Map<String, Object> valueMap = ctx.asObjectMap();
        valueMap.put(FLAG_KEY, flagKey);

        try {
            return jsonLogicHandler.apply(targetingRule, valueMap);
        } catch (JsonLogicException e) {
            throw new TargetingRuleException("Error evaluating json logic", e);
        }
    }

    @Getter
    static class FlagProperties {
        private final String flagKey;
        private final String targetingKey;

        FlagProperties(Object from) {
            if (from instanceof Map) {
                Map<?, ?> dataMap = (Map<?, ?>) from;

                Object fKey = dataMap.get(FLAG_KEY);

                if (fKey instanceof String) {
                    flagKey = (String) fKey;
                } else {
                    flagKey = null;
                }

                Object tKey = dataMap.get(TARGET_KEY);

                if (tKey instanceof String) {
                    targetingKey = (String) tKey;
                } else {
                    targetingKey = null;
                }
            } else {
                flagKey = null;
                targetingKey = null;
            }
        }
    }
}
