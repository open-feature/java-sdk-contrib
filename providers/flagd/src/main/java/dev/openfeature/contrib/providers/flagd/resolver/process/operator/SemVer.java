package dev.openfeature.contrib.providers.flagd.resolver.process.operator;

import io.github.jamsesso.jsonlogic.ast.JsonLogicArray;
import io.github.jamsesso.jsonlogic.evaluator.JsonLogicEvaluationException;
import io.github.jamsesso.jsonlogic.evaluator.JsonLogicEvaluator;
import io.github.jamsesso.jsonlogic.evaluator.JsonLogicExpression;

public class SemVer implements JsonLogicExpression {

    // todo find sem ver impl library


    public String key() {
        return "sem_ver";
    }

    public Object evaluate(JsonLogicEvaluator evaluator, JsonLogicArray arguments, Object data)
            throws JsonLogicEvaluationException {

        // todo implement
        return null;
    }
}
