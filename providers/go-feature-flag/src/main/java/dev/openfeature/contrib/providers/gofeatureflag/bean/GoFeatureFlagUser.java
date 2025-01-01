package dev.openfeature.contrib.providers.gofeatureflag.bean;

import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.TargetingKeyMissingError;
import java.util.HashMap;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

/** GoFeatureFlagUser is the representation of a user for GO Feature Flag. */
@Builder
@Getter
public class GoFeatureFlagUser {
    private static final String anonymousFieldName = "anonymous";
    private final String key;
    private final boolean anonymous;
    private final Map<String, Object> custom;

    /**
     * fromEvaluationContext is transforming the evaluationContext into a GoFeatureFlagUser.
     *
     * @param ctx - EvaluationContext from open-feature
     * @return GoFeatureFlagUser format for GO Feature Flag
     */
    public static GoFeatureFlagUser fromEvaluationContext(EvaluationContext ctx) {
        String key = ctx.getTargetingKey();
        if (key == null || key.isEmpty()) {
            throw new TargetingKeyMissingError();
        }
        boolean anonymous = isAnonymousUser(ctx);
        Map<String, Object> custom = new HashMap<>(ctx.asObjectMap());
        if (ctx.getValue(anonymousFieldName) != null) {
            custom.remove(anonymousFieldName);
        }
        return GoFeatureFlagUser.builder()
                .anonymous(anonymous)
                .key(key)
                .custom(custom)
                .build();
    }

    /**
     * isAnonymousUser is checking if the user in the evaluationContext is anonymous.
     *
     * @param ctx - EvaluationContext from open-feature
     * @return true if the user is anonymous, false otherwise
     */
    public static boolean isAnonymousUser(EvaluationContext ctx) {
        Value value = ctx.getValue(anonymousFieldName);
        return value != null && value.asBoolean();
    }
}
