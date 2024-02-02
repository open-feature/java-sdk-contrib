package dev.openfeature.contrib.providers.statsig;

import com.statsig.sdk.StatsigUser;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.Structure;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.InvalidContextError;

import java.util.HashMap;
import java.util.Map;

/**
 * Transformer from OpenFeature context to statsig User.
 */
class ContextTransformer {
    public static final String CONTEXT_APP_VERSION = "appVersion";
    public static final String CONTEXT_COUNTRY = "country";
    public static final String CONTEXT_EMAIL = "email";
    public static final String CONTEXT_IP = "ip";
    public static final String CONTEXT_LOCALE = "locale";
    public static final String CONTEXT_USER_AGENT = "userAgent";
    public static final String CONTEXT_PRIVATE_ATTRIBUTES = "privateAttributes";

    static StatsigUser transform(EvaluationContext ctx) {
        if (ctx.getTargetingKey() == null) {
            throw new TargetingKeyMissingError("targeting key is required.");
        }
        StatsigUser user = new StatsigUser(ctx.getTargetingKey());
        Map<String, String> customMap = new HashMap<>();
        ctx.asObjectMap().forEach((k, v) -> {
            switch (k) {
                case CONTEXT_APP_VERSION:
                    user.setAppVersion(String.valueOf(v));
                    break;
                case CONTEXT_COUNTRY:
                    user.setCountry(String.valueOf(v));
                    break;
                case CONTEXT_EMAIL:
                    user.setEmail(String.valueOf(v));
                    break;
                case CONTEXT_IP:
                    user.setIp(String.valueOf(v));
                    break;
                case CONTEXT_USER_AGENT:
                    user.setUserAgent(String.valueOf(v));
                    break;
                case CONTEXT_LOCALE:
                    user.setLocale(String.valueOf(v));
                    break;
                default:
                    if (!CONTEXT_PRIVATE_ATTRIBUTES.equals(k)) {
                        customMap.put(k, String.valueOf(v));
                    }
                    break;
            }
        });
        user.setCustomIDs(customMap);

        Map<String, String> privateMap = new HashMap<>();
        Value privateAttributes = ctx.getValue(CONTEXT_PRIVATE_ATTRIBUTES);
        if (privateAttributes != null && privateAttributes.isStructure()) {
            Structure privateAttributesStructure = privateAttributes.asStructure();
            privateAttributesStructure.asObjectMap().forEach((k, v) -> privateMap.put(k, String.valueOf(v)));
            user.setPrivateAttributes(privateMap);
        }
        return user;
    }

}
