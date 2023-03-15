package dev.openfeature.contrib.providers.inlineeval;

import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.Value;
import io.github.jamsesso.jsonlogic.JsonLogic;
import io.github.jamsesso.jsonlogic.JsonLogicException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import java.util.Map;

class InlineEvaluatingProviderTest {
    @Test
    public void demonstrateJsonLogic() throws JsonLogicException {
        // if specific id matches or category is in valid set, yes. Otherwise, no.
        var rule = """
                {"if": [
                  {"or": [
                    {"in": [{"var": "userId"}, [1,2,3,4]]},
                    {"in": [{"var": "category"}, ["pies", "cakes"]]},
                  ]},
                  true,
                  false
                ]}
                """;
        var logic = new JsonLogic();
        assertEquals(false, logic.apply(rule, null));
        assertEquals(true, logic.apply(rule, Map.of("userId", 2)));
        assertEquals(false, logic.apply(rule, Map.of("userId", 5)));
        assertEquals(true, logic.apply(rule, Map.of("category", "pies")));
        assertEquals(false, logic.apply(rule, Map.of("category", "muffins")));
    }


    @Test
    public void jsonlogicReturnTypes() throws JsonLogicException {
        // if specific id matches or category is in valid set, yes. Otherwise, no.
        var rule = """
            {"if": [{"var": "bool"}, true,
              {"if": [{"var": "string"}, "yes",
                {"if": [{"var": "double"}, 4.2,
                  2
                ]}
              ]}
            ]}
            """;
        var logic = new JsonLogic();
        assertEquals(2D, logic.apply(rule, null));
        assertEquals(4.2D, logic.apply(rule, Map.of("double", true)));
        assertEquals("yes", logic.apply(rule, Map.of("string", true)));
        assertEquals(true, logic.apply(rule, Map.of("bool", "true")));
    }

    @Test public void providerTest() {
        var iep = new InlineEvaluatingProvider(new JsonLogic(), new ExampleFetcher());
        var evalCtx = new ImmutableContext(Map.of("userId", new Value(2)));

        var result = iep.getBooleanEvaluation("test-key", false, evalCtx);
        assertTrue(result.getValue(), result.getReason());
    }

    @Test public void callsFetcherInitialize() {
        var mockFetcher = mock(RuleFetcher.class);
        var iep = new InlineEvaluatingProvider(new JsonLogic(), mockFetcher);
        iep.initialize(null);
        verify(mockFetcher).initialize(any());
    }
}