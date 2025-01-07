package dev.openfeature.contrib.providers.unleash;

import dev.openfeature.sdk.EvaluationContext;
import io.getunleash.UnleashContext;
import java.time.ZonedDateTime;

/** Transformer from Unleash context to OpenFeature context and vice versa. */
public class ContextTransformer {

    public static final String CONTEXT_APP_NAME = "appName";
    public static final String CONTEXT_USER_ID = "userId";
    public static final String CONTEXT_ENVIRONMENT = "environment";
    public static final String CONTEXT_REMOTE_ADDRESS = "remoteAddress";
    public static final String CONTEXT_SESSION_ID = "sessionId";
    public static final String CONTEXT_CURRENT_TIME = "currentTime";

    protected static UnleashContext transform(EvaluationContext ctx) {
        UnleashContext.Builder unleashContextBuilder = new UnleashContext.Builder();
        ctx.asObjectMap().forEach((k, v) -> {
            switch (k) {
                case CONTEXT_APP_NAME:
                    unleashContextBuilder.appName(String.valueOf(v));
                    break;
                case CONTEXT_USER_ID:
                    unleashContextBuilder.userId(String.valueOf(v));
                    break;
                case CONTEXT_ENVIRONMENT:
                    unleashContextBuilder.environment(String.valueOf(v));
                    break;
                case CONTEXT_REMOTE_ADDRESS:
                    unleashContextBuilder.remoteAddress(String.valueOf(v));
                    break;
                case CONTEXT_SESSION_ID:
                    unleashContextBuilder.sessionId(String.valueOf(v));
                    break;
                case CONTEXT_CURRENT_TIME:
                    unleashContextBuilder.currentTime(ZonedDateTime.parse(String.valueOf(v)));
                    break;
                default:
                    unleashContextBuilder.addProperty(k, String.valueOf(v));
                    break;
            }
        });
        return unleashContextBuilder.build();
    }
}
