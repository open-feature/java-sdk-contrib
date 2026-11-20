package dev.openfeature.contrib.providers.jsonlogic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.Hook;
import dev.openfeature.sdk.HookContext;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Value;
import io.github.jamsesso.jsonlogic.JsonLogic;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class JsonlogicProviderTest {
    @Test
    public void demonstrateJsonLogic() throws Exception {
        // if specific id matches or category is in valid set, yes. Otherwise, no.
        String rule = Utils.readTestResource("/dessert-decider.json");

        JsonLogic logic = new JsonLogic();
        assertEquals(false, logic.apply(rule, new HashMap<String, String>()));
        assertEquals(true, logic.apply(rule, Collections.singletonMap("userId", 2)));
        assertEquals(false, logic.apply(rule, Collections.singletonMap("userId", 5)));
        assertEquals(true, logic.apply(rule, Collections.singletonMap("category", "pies")));
        assertEquals(false, logic.apply(rule, Collections.singletonMap("category", "muffins")));
    }

    @Test
    public void jsonlogicReturnTypes() throws Exception {
        // if specific id matches or category is in valid set, yes. Otherwise, no.

        String rule = Utils.readTestResource("/many-types.json");
        JsonLogic logic = new JsonLogic();
        assertEquals(2D, logic.apply(rule, Collections.emptyMap()));
        assertEquals(4.2D, logic.apply(rule, Collections.singletonMap("double", true)));
        assertEquals("yes", logic.apply(rule, Collections.singletonMap("string", true)));
        assertEquals(true, logic.apply(rule, Collections.singletonMap("bool", "true")));
    }

    @Test
    public void providerTest() throws Exception {
        URL v = this.getClass().getResource("/test-rules.json");
        JsonlogicProvider iep = new JsonlogicProvider(new FileBasedFetcher(v.toURI()));
        ImmutableContext evalCtx = new ImmutableContext(Collections.singletonMap("userId", new Value(2)));

        ProviderEvaluation<Boolean> result = iep.getBooleanEvaluation("should-have-dessert?", false, evalCtx);
        assertTrue(result.getValue(), result.getReason());
    }

    @Test
    public void missingKey() throws Exception {
        URL v = this.getClass().getResource("/test-rules.json");
        JsonlogicProvider iep = new JsonlogicProvider(new FileBasedFetcher(v.toURI()));

        ProviderEvaluation<Boolean> result = iep.getBooleanEvaluation("missingKey", false, null);
        assertEquals("Unable to find rules for the given key", result.getErrorMessage());
        assertEquals("ERROR", result.getReason());
        assertEquals(ErrorCode.FLAG_NOT_FOUND, result.getErrorCode());
        assertEquals(false, result.getValue());
    }

    @Test
    public void missingKeyThroughClientRunsErrorHooks() throws Exception {
        URL v = this.getClass().getResource("/test-rules.json");
        OpenFeatureAPI api = OpenFeatureAPI.getInstance();
        api.setProviderAndWait("jsonlogic-missing-key", new JsonlogicProvider(new FileBasedFetcher(v.toURI())));
        AtomicBoolean errorHookCalled = new AtomicBoolean();
        Client client = api.getClient("jsonlogic-missing-key");
        client.addHooks(new Hook<Boolean>() {
            @Override
            public void error(HookContext<Boolean> ctx, Exception error, Map<String, Object> hints) {
                errorHookCalled.set(true);
            }
        });

        FlagEvaluationDetails<Boolean> details = client.getBooleanDetails("missingKey", true);

        assertEquals(true, details.getValue());
        assertEquals(ErrorCode.FLAG_NOT_FOUND, details.getErrorCode());
        assertTrue(errorHookCalled.get());
    }

    @Test
    public void numberRules() throws Exception {
        URL v = this.getClass().getResource("/test-rules.json");
        JsonlogicProvider iep = new JsonlogicProvider(new FileBasedFetcher(v.toURI()));
        ImmutableContext doubleCtx = new ImmutableContext(Collections.singletonMap("double", new Value(true)));

        assertEquals(
                2,
                iep.getIntegerEvaluation("many-types", 0, new ImmutableContext())
                        .getValue());
        assertEquals(4.2D, iep.getDoubleEvaluation("many-types", 0D, doubleCtx).getValue());
    }

    @Test
    public void typeMismatch() throws Exception {
        URL v = this.getClass().getResource("/test-rules.json");
        JsonlogicProvider iep = new JsonlogicProvider(new FileBasedFetcher(v.toURI()));
        ImmutableContext stringCtx = new ImmutableContext(Collections.singletonMap("string", new Value(true)));

        ProviderEvaluation<Boolean> asBoolean = iep.getBooleanEvaluation("many-types", false, stringCtx);
        assertEquals(false, asBoolean.getValue());
        assertEquals(ErrorCode.TYPE_MISMATCH, asBoolean.getErrorCode());
        assertEquals("Rule for key many-types returned String, expected Boolean", asBoolean.getErrorMessage());

        ProviderEvaluation<Integer> asInteger = iep.getIntegerEvaluation("many-types", 7, stringCtx);
        assertEquals(7, asInteger.getValue());
        assertEquals(ErrorCode.TYPE_MISMATCH, asInteger.getErrorCode());

        ProviderEvaluation<String> asString = iep.getStringEvaluation("many-types", "default", new ImmutableContext());
        assertEquals("default", asString.getValue());
        assertEquals(ErrorCode.TYPE_MISMATCH, asString.getErrorCode());
    }

    @Test
    public void callsFetcherInitialize() {
        RuleFetcher mockFetcher = mock(RuleFetcher.class);
        JsonlogicProvider iep = new JsonlogicProvider(mockFetcher);
        iep.initialize(null);
        verify(mockFetcher).initialize(any());
    }
}
