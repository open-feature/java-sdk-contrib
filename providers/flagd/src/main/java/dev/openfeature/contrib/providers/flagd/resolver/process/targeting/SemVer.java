package dev.openfeature.contrib.providers.flagd.resolver.process.targeting;

import io.github.jamsesso.jsonlogic.evaluator.JsonLogicEvaluationException;
import io.github.jamsesso.jsonlogic.evaluator.expressions.PreEvaluatedArgumentsExpression;
import lombok.extern.slf4j.Slf4j;
import org.semver4j.Semver;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
class SemVer implements PreEvaluatedArgumentsExpression {

    private static final String EQ = "=";
    private static final String NEQ = "!=";
    private static final String LT = "<";
    private static final String LTE = "<=";
    private static final String GT = ">";
    private static final String GTE = ">=";
    private static final String MAJOR = "^";
    private static final String MINOR = "~";

    private static final Set<String> OPS = new HashSet<>();

    static {
        OPS.add(EQ);
        OPS.add(NEQ);
        OPS.add(LT);
        OPS.add(LTE);
        OPS.add(GT);
        OPS.add(GTE);
        OPS.add(MAJOR);
        OPS.add(MINOR);
    }

    public String key() {
        return "sem_ver";
    }

    public Object evaluate(List arguments, Object data) throws JsonLogicEvaluationException {

        if (arguments.size() != 3) {
            log.debug("Incorrect number of arguments for sem_ver operator");
            return null;
        }

        for (int i = 0; i < 3; i++) {
            if (!(arguments.get(i) instanceof String)) {
                log.debug("Invalid argument type. Require Strings");
                return null;
            }
        }

        // arg 1 should be a SemVer
        final Semver arg1Parsed;

        if ((arg1Parsed = Semver.parse((String) arguments.get(0))) == null) {
            log.debug("Argument one is not a valid SemVer");
            return null;
        }

        // arg 2 should be the supported operator
        final String arg2Parsed = (String) arguments.get(1);

        if (!OPS.contains(arg2Parsed)) {
            log.debug(String.format("Not valid operator in argument 2. Received: %s", arg2Parsed));
            return null;
        }

        // arg 3 should be a SemVer
        final Semver arg3Parsed;

        if ((arg3Parsed = Semver.parse((String) arguments.get(2))) == null) {
            log.debug("Argument three is not a valid SemVer");
            return null;
        }

        return compare(arg2Parsed, arg1Parsed, arg3Parsed);
    }

    private static boolean compare(final String operator, final Semver arg1, final Semver arg2)
            throws JsonLogicEvaluationException {

        int comp = arg1.compareTo(arg2);

        switch (operator) {
            case EQ:
                return comp == 0;
            case NEQ:
                return comp != 0;
            case LT:
                return comp < 0;
            case LTE:
                return comp <= 0;
            case GT:
                return comp > 0;
            case GTE:
                return comp >= 0;
            case MAJOR:
                return arg1.getMajor() == arg2.getMajor();
            case MINOR:
                return arg1.getMinor() == arg2.getMinor() && arg1.getMajor() == arg2.getMajor();
            default:
                throw new JsonLogicEvaluationException(
                        String.format("Unsupported operator received. Operator: %s", operator));
        }
    }

}
