package dev.openfeature.contrib.providers.flagd.resolver.process.operator;

import io.github.jamsesso.jsonlogic.ast.JsonLogicArray;
import io.github.jamsesso.jsonlogic.ast.JsonLogicNode;
import io.github.jamsesso.jsonlogic.ast.JsonLogicString;
import io.github.jamsesso.jsonlogic.evaluator.JsonLogicEvaluationException;
import io.github.jamsesso.jsonlogic.evaluator.JsonLogicEvaluator;
import io.github.jamsesso.jsonlogic.evaluator.JsonLogicExpression;

public class StringComp implements JsonLogicExpression {
    private final Type type;

    public StringComp(Type type){
        this.type = type;
    }

    public String key() {
        return type.key;
    }

    public Object evaluate(JsonLogicEvaluator evaluator, JsonLogicArray arguments, Object data)
            throws JsonLogicEvaluationException {
        if (arguments.size() != 2){
            return null;
        }

        JsonLogicNode jsonLogicNode = arguments.get(0);

        if (!(jsonLogicNode instanceof JsonLogicString)){
            return null;
        }

        final String arg1 = ((JsonLogicString) jsonLogicNode).getValue();

        jsonLogicNode = arguments.get(1);

        if (!(jsonLogicNode instanceof JsonLogicString)){
            return null;
        }

        final String arg2 = ((JsonLogicString) jsonLogicNode).getValue();

        switch (this.type){
            case STARTS_WITH:
                return arg1.startsWith(arg2);
            case ENDS_WITH:
                return arg1.endsWith(arg2);
            default:
                throw new JsonLogicEvaluationException(String.format("Unknown string comparison evaluator type %s",
                        this.type));
        }
    }


    public enum Type{
         STARTS_WITH("starts_with"),
         ENDS_WITH("ends_with");

        private final String key;

        Type(String key) {
            this.key = key;
        }
    }
}
