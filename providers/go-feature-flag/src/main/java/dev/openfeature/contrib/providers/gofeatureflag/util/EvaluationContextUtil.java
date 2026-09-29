package dev.openfeature.contrib.providers.gofeatureflag.util;

import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.Value;

/**
 * EvaluationContextUtil is a utility class that provides methods to work with the evaluation context.
 * It is used to check if the user is anonymous or not.
 */
public class EvaluationContextUtil {
    /**
     * anonymousFieldName is the name of the field in the evaluation context that indicates
     * if the user is anonymous.
     */
    private static final String anonymousFieldName = "anonymous";

    private static final String ANONYMOUS_USER_CONTEXT_KIND = "anonymousUser";
    private static final String USER_CONTEXT_KIND = "user";

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
        return value != null && value.isBoolean() && Boolean.TRUE.equals(value.asBoolean());
    }

    /**
     * contextKind is the bucket an event is counted under.
     *
     * @param ctx - EvaluationContext from open-feature
     * @return the bucket this evaluation belongs to
     */
    public static String contextKind(final EvaluationContext ctx) {
        return isAnonymousUser(ctx) ? ANONYMOUS_USER_CONTEXT_KIND : USER_CONTEXT_KIND;
    }

    /**
     * userKey is the key an event is attributed to.
     *
     * @param ctx - EvaluationContext from open-feature
     * @return the targeting key, or a placeholder when there is none
     */
    public static String userKey(final EvaluationContext ctx) {
        if (ctx == null
                || ctx.getTargetingKey() == null
                || ctx.getTargetingKey().isEmpty()) {
            return Const.UNDEFINED_TARGETING_KEY;
        }
        return ctx.getTargetingKey();
    }
}
