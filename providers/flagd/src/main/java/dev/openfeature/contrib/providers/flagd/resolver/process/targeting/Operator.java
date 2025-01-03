package dev.openfeature.contrib.providers.flagd.resolver.process.targeting;

import dev.openfeature.sdk.EvaluationContext;
import io.github.jamsesso.jsonlogic.JsonLogic;
import io.github.jamsesso.jsonlogic.JsonLogicException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;

/**
 * Targeting operator wraps JsonLogic handlers and expose a simple API for external layers. This
 * helps to isolate external dependencies to this package.
 */
public class Operator {

    static final String FLAGD_PROPS_KEY = "$flagd";
    static final String FLAG_KEY = "flagKey";
    static final String TARGET_KEY = "targetingKey";
    static final String TIME_STAMP = "timestamp";

    private final JsonLogic jsonLogicHandler;

    /** Construct a targeting operator. */
    public Operator() {
        jsonLogicHandler = new JsonLogic();
        jsonLogicHandler.addOperation(new Fractional());
        jsonLogicHandler.addOperation(new SemVer());
        jsonLogicHandler.addOperation(new StringComp(StringComp.Type.STARTS_WITH));
        jsonLogicHandler.addOperation(new StringComp(StringComp.Type.ENDS_WITH));
    }

    /** Apply this operator on the provided rule. */
    public Object apply(final String flagKey, final String targetingRule, final EvaluationContext ctx)
            throws TargetingRuleException {
        final Map<String, Object> flagdProperties = new HashMap<>();
        flagdProperties.put(FLAG_KEY, flagKey);

        long unixTimestamp = Instant.now().getEpochSecond();
        flagdProperties.put(TIME_STAMP, unixTimestamp);

        final Map<String, Object> targetingCtxData = ctx.asObjectMap();

        // asObjectMap() does not provide explicitly set targeting key (ex:- new
        // ImmutableContext("TargetingKey") ).
        // Hence, we add this explicitly here for targeting rule processing.
        targetingCtxData.put(TARGET_KEY, ctx.getTargetingKey());
        targetingCtxData.put(FLAGD_PROPS_KEY, flagdProperties);

        try {
            return jsonLogicHandler.apply(targetingRule, targetingCtxData);
        } catch (JsonLogicException e) {
            throw new TargetingRuleException("Error evaluating json logic", e);
        }
    }

    /**
     * A utility class to extract well-known properties such as flag key, targeting key and timestamp
     * from json logic evaluation context data for further processing at evaluators.
     */
    @Getter
    static class FlagProperties {
        private Object flagKey = null;
        private Object timestamp = null;
        private String targetingKey = null;

        FlagProperties(Object from) {
            if (!(from instanceof Map)) {
                return;
            }

            final Map<?, ?> dataMap = (Map<?, ?>) from;
            final Object targetKey = dataMap.get(TARGET_KEY);
            if (targetKey instanceof String) {
                targetingKey = (String) targetKey;
            }

            final Map<?, ?> flagdPropertyMap = flagdPropertyMap(dataMap);
            if (flagdPropertyMap == null) {
                return;
            }

            this.flagKey = flagdPropertyMap.get(FLAG_KEY);
            this.timestamp = flagdPropertyMap.get(TIME_STAMP);
        }

        private static Map<?, ?> flagdPropertyMap(Map<?, ?> dataMap) {
            Object o = dataMap.get(FLAGD_PROPS_KEY);
            if (o instanceof Map) {
                return (Map<?, ?>) o;
            }

            return null;
        }
    }
}
