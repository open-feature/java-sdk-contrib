package dev.openfeature.contrib.providers.flagd.e2e.steps;

import dev.openfeature.contrib.providers.flagd.e2e.State;
import dev.openfeature.sdk.ImmutableStructure;
import dev.openfeature.sdk.MutableContext;
import dev.openfeature.sdk.Value;
import io.cucumber.java.en.Given;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@Isolated()
public class ContextSteps extends AbstractSteps {


    public ContextSteps(State state) {
        super(state);
    }


    @Given("a context containing a key {string}, with type {string} and with value {string}")
    public void a_context_containing_a_key_with_type_and_with_value(String key, String type, String value) throws ClassNotFoundException, InstantiationException {
        Map<String, Value> map = state.context.asMap();
        map.put(key, new Value(value));
        state.context = new MutableContext(state.context.getTargetingKey(), map);
    }

    @Given("a context containing a targeting key with value {string}")
    public void a_context_containing_a_targeting_key_with_value(String string) {
        state.context.setTargetingKey(string);
    }

    @Given("a context containing a nested property with outer key {string} and inner key {string}, with value {string}")
    public void a_context_containing_a_nested_property_with_outer_key_and_inner_key_with_value(String outer, String inner, String value) {
        Map<String, Value> innerMap = new HashMap<>();
        innerMap.put(inner, new Value(value));
        state.context.add(outer, new ImmutableStructure(innerMap));
    }

}
