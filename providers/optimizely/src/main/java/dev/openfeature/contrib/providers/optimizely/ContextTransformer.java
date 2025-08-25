package dev.openfeature.contrib.providers.optimizely;

import com.optimizely.ab.Optimizely;
import com.optimizely.ab.OptimizelyUserContext;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.exceptions.TargetingKeyMissingError;
import java.util.HashMap;
import java.util.Map;
import lombok.Builder;

/** Transformer from OpenFeature context to OptimizelyUserContext. */
@Builder
class ContextTransformer {
    public static final String CONTEXT_APP_VERSION = "appVersion";
    public static final String CONTEXT_COUNTRY = "country";
    public static final String CONTEXT_EMAIL = "email";
    public static final String CONTEXT_IP = "ip";
    public static final String CONTEXT_LOCALE = "locale";
    public static final String CONTEXT_USER_AGENT = "userAgent";
    public static final String CONTEXT_PRIVATE_ATTRIBUTES = "privateAttributes";

    private Optimizely optimizely;

    public OptimizelyUserContext transform(EvaluationContext ctx) {
        if (ctx.getTargetingKey() == null) {
            throw new TargetingKeyMissingError("targeting key is required.");
        }
        Map<String, Object> attributes = new HashMap<>();
        attributes.putAll(ctx.asObjectMap());
        return optimizely.createUserContext(ctx.getTargetingKey(), attributes);
    }
}
