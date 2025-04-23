package dev.openfeature.contrib.providers.gofeatureflag.util;

import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.Value;

public class EvaluationContextUtil {
    /** anonymousFieldName is the name of the field in the evaluation context that indicates if the user is anonymous. */
    private static final String anonymousFieldName = "anonymous";

    /**
     * isAnonymousUser is checking if the user in the evaluationContext is anonymous.
     *
     * @param ctx - EvaluationContext from open-feature
     * @return true if the user is anonymous, false otherwise
     */
    public static boolean isAnonymousUser(final EvaluationContext ctx) {
        if (ctx == null) {
            return true;
        }
        Value value = ctx.getValue(anonymousFieldName);
        return value != null && value.asBoolean();
    }
}
