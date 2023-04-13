package dev.openfeature.contrib.providers.jsonlogic;

import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Value;
import io.github.jamsesso.jsonlogic.JsonLogic;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.Collections;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

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

    @Test public void providerTest() throws Exception {
        URL v = this.getClass().getResource("/test-rules.json");
        JsonlogicProvider iep = new JsonlogicProvider(new FileBasedFetcher(v.toURI()));
        ImmutableContext evalCtx = new ImmutableContext(Collections.singletonMap("userId", new Value(2)));

        ProviderEvaluation<Boolean> result = iep.getBooleanEvaluation("should-have-dessert?", false, evalCtx);
        assertTrue(result.getValue(), result.getReason());
    }

    @Test public void missingKey() throws Exception {
        URL v = this.getClass().getResource("/test-rules.json");
        JsonlogicProvider iep = new JsonlogicProvider(new FileBasedFetcher(v.toURI()));

        ProviderEvaluation<Boolean> result = iep.getBooleanEvaluation("missingKey", false, null);
        assertEquals("Unable to find rules for the given key", result.getErrorMessage());
        assertEquals("ERROR", result.getReason());
    }

    @Test public void callsFetcherInitialize() {
        RuleFetcher mockFetcher = mock(RuleFetcher.class);
        JsonlogicProvider iep = new JsonlogicProvider(mockFetcher);
        iep.initialize(null);
        verify(mockFetcher).initialize(any());
    }
}