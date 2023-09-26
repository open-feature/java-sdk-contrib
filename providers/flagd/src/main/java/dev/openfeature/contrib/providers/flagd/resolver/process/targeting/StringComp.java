package dev.openfeature.contrib.providers.flagd.resolver.process.targeting;

import io.github.jamsesso.jsonlogic.evaluator.JsonLogicEvaluationException;
import io.github.jamsesso.jsonlogic.evaluator.expressions.PreEvaluatedArgumentsExpression;
import lombok.extern.java.Log;

import java.util.List;
import java.util.logging.Level;

@Log
class StringComp implements PreEvaluatedArgumentsExpression {
    private final Type type;

    StringComp(Type type) {
        this.type = type;
    }

    public String key() {
        return type.key;
    }

    public Object evaluate(List arguments, Object data) throws JsonLogicEvaluationException {
        if (arguments.size() != 2) {
            log.log(Level.FINE, "Incorrect number of arguments for String comparison operator");
            return null;
        }

        Object jsonLogicNode = arguments.get(0);

        if (!(jsonLogicNode instanceof String)) {
            log.log(Level.FINE, "Incorrect argument type for first argument");
            return null;
        }

        final String arg1 = (String) jsonLogicNode;

        jsonLogicNode = arguments.get(1);

        if (!(jsonLogicNode instanceof String)) {
            log.log(Level.FINE, "Incorrect argument type for second argument");
            return null;
        }

        final String arg2 = (String) jsonLogicNode;

        switch (this.type) {
            case STARTS_WITH:
                return arg1.startsWith(arg2);
            case ENDS_WITH:
                return arg1.endsWith(arg2);
            default:
                log.log(Level.FINE, String.format("Unknown string comparison evaluator type %s", this.type));
                return null;
        }
    }


    enum Type {
        STARTS_WITH("starts_with"),
        ENDS_WITH("ends_with");

        private final String key;

        Type(String key) {
            this.key = key;
        }
    }
}
