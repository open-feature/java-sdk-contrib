package org.gofeatureflag.provider.bean;


import com.fasterxml.jackson.annotation.JsonInclude;
import dev.openfeature.javasdk.EvaluationContext;
import dev.openfeature.javasdk.Value;
import lombok.Builder;
import lombok.Getter;
import org.gofeatureflag.provider.exception.InvalidTargetingKey;

import java.util.Map;

@Builder
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GoFeatureFlagUser {
    private final String key;
    private final boolean anonymous;
    private final Map<String, Object> custom;
    private static final String anonymousFieldName = "anonymous";
    /**
     * fromEvaluationContext is transforming the evaluationContext into a GoFeatureFlagUser
     *
     * @param ctx - EvaluationContext from open-feature
     * @return GoFeatureFlagUser format for GO Feature Flag
     */
    public static GoFeatureFlagUser fromEvaluationContext(EvaluationContext ctx) {
        String key = ctx.getTargetingKey();
        if (key == null || "".equals(key)) { throw new InvalidTargetingKey(); }

        Value anonymousValue = ctx.getValue(anonymousFieldName);
        boolean anonymous = anonymousValue != null && anonymousValue.isBoolean() ? anonymousValue.asBoolean() : false;
        Map<String, Object> custom = ctx.asObjectMap();
        if (ctx.getValue(anonymousFieldName) != null) {
            custom.remove(anonymousFieldName);
        }
        return GoFeatureFlagUser.builder().anonymous(anonymous).key(key).custom(custom).build();
    }
}
