package dev.openfeature.contrib.providers.flagd.resolver.process.targeting;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import dev.openfeature.sdk.EvaluationContext;
import io.github.jamsesso.jsonlogic.JsonLogic;
import io.github.jamsesso.jsonlogic.JsonLogicException;
import lombok.Getter;


/**
 * Targeting operator wraps JsonLogic handlers and expose a simple API for external layers.
 * This helps to isolate external dependencies to this package.
 */
public class Operator {

    static final String FLAGD_PROPS_KEY = "$flagd";
    static final String FLAG_KEY = "flagKey";
    static final String TARGET_KEY = "targetingKey";

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

    /**
     * Apply this operator on the provided rule.
     */
    public Object apply(final String flagKey, final String targetingRule, final EvaluationContext ctx)
            throws TargetingRuleException {
        final Map<String, String> flagdProperties = new HashMap<>();
        flagdProperties.put(FLAG_KEY, flagKey);
        final Map<String, Object> valueMap = ctx.asObjectMap();
        valueMap.put(FLAGD_PROPS_KEY, flagdProperties);

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

                Object flagKey = Optional.ofNullable(dataMap.get(FLAGD_PROPS_KEY))
                    .filter(flagdProps -> flagdProps instanceof Map)
                    .map(flagdProps -> ((Map<?, ?>)flagdProps).get(FLAG_KEY))
                    .orElse(null);

                if (flagKey instanceof String) {
                    this.flagKey = (String) flagKey;
                } else {
                    this.flagKey = null;
                }

                Object targetKey = dataMap.get(TARGET_KEY);

                if (targetKey instanceof String) {
                    targetingKey = (String) targetKey;
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
