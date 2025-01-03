package dev.openfeature.contrib.providers.configcat;

import com.configcat.User;
import dev.openfeature.sdk.EvaluationContext;
import java.util.HashMap;
import java.util.Map;

/** Transformer from OpenFeature context to ConfigCat User. */
public class ContextTransformer {

    public static final String CONTEXT_EMAIL = "Email";
    public static final String CONTEXT_COUNTRY = "Country";

    protected static User transform(EvaluationContext ctx) {
        User.Builder userBuilder = User.newBuilder();
        Map<String, Object> customMap = new HashMap<>();
        ctx.asObjectMap().forEach((k, v) -> {
            switch (k) {
                case CONTEXT_COUNTRY:
                    userBuilder.country(String.valueOf(v));
                    break;
                case CONTEXT_EMAIL:
                    userBuilder.email(String.valueOf(v));
                    break;
                default:
                    customMap.put(k, String.valueOf(v));
                    break;
            }
        });
        userBuilder.custom(customMap);
        return userBuilder.build(ctx.getTargetingKey());
    }
}
