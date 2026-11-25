package dev.openfeature.contrib.tools.flagd.api.testkit;

import dev.openfeature.sdk.ImmutableStructure;
import dev.openfeature.sdk.MutableContext;
import dev.openfeature.sdk.Value;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cucumber step definitions for building up evaluation context.
 */
public class ContextSteps {

    private final EvaluatorState state;

    /** Constructs steps with shared scenario state injected by PicoContainer. */
    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "Intentional mutable state sharing required by Cucumber PicoContainer DI")
    public ContextSteps(EvaluatorState state) {
        this.state = state;
    }

    /** Adds a typed key/value pair to the evaluation context. */
    @Given("^a context containing a key \"([^\"]*)\", with type \"([^\"]*)\" and with value \"(.*)\"$")
    public void contextKeyWithTypeAndValue(String key, String type, String value) throws IOException {
        Map<String, Value> map = new HashMap<>(state.context.asMap());
        map.put(key, Value.objectToValue(EvaluatorUtils.convert(value, type)));
        state.context = new MutableContext(state.context.getTargetingKey(), map);
    }

    /** Adds multiple context keys from a data table. */
    @Given("a context with the following keys:")
    public void contextWithFollowingKeys(DataTable dataTable) throws IOException {
        List<Map<String, String>> rows = dataTable.asMaps(String.class, String.class);
        Map<String, Value> map = new HashMap<>(state.context.asMap());
        for (Map<String, String> row : rows) {
            String key = row.get("key");
            String type = row.get("type");
            String value = row.get("value");
            map.put(key, Value.objectToValue(EvaluatorUtils.convert(value, type)));
        }
        state.context = new MutableContext(state.context.getTargetingKey(), map);
    }

    /** Sets the targeting key on the evaluation context. */
    @Given("a context containing a targeting key with value {string}")
    public void contextTargetingKey(String targetingKey) {
        state.context.setTargetingKey(targetingKey);
    }

    /** Adds a nested structure (outer.inner = value) to the evaluation context. */
    @Given("a context containing a nested property with outer key {string} and inner key {string}, with value {string}")
    public void contextNestedProperty(String outer, String inner, String value) {
        Map<String, Value> innerMap = new HashMap<>();
        innerMap.put(inner, new Value(value));
        state.context.add(outer, new ImmutableStructure(innerMap));
    }
}
