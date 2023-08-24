package dev.openfeature.contrib.providers.gofeatureflag.bean;

import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.TargetingKeyMissingError;
import lombok.Builder;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * GoFeatureFlagUser is the representation of a user for GO Feature Flag.
 */
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
        if (key == null || "".equals(key)) {
            throw new TargetingKeyMissingError();
        }
        Value anonymousValue = ctx.getValue(anonymousFieldName);
        if (anonymousValue == null) {
            anonymousValue = new Value(Boolean.FALSE);
        }
        boolean anonymous = anonymousValue.asBoolean();
        Map<String, Object> custom = new HashMap<>(ctx.asObjectMap());
        if (ctx.getValue(anonymousFieldName) != null) {
            custom.remove(anonymousFieldName);
        }
        return GoFeatureFlagUser.builder().anonymous(anonymous).key(key).custom(custom).build();
    }
}
