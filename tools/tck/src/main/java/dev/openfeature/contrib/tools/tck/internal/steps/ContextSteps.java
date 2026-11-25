package dev.openfeature.contrib.tools.tck.internal.steps;

import dev.openfeature.contrib.tools.tck.BackendControl;
import dev.openfeature.contrib.tools.tck.ProviderTckHarness;
import dev.openfeature.contrib.tools.tck.TckState;
import dev.openfeature.contrib.tools.tck.internal.TckValues;
import dev.openfeature.sdk.MutableContext;
import dev.openfeature.sdk.MutableStructure;
import dev.openfeature.sdk.Value;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import java.util.HashMap;
import java.util.Map;

/**
 * Steps that build the evaluation context passed to every evaluation.
 */
public class ContextSteps extends AbstractSteps {

    public ContextSteps(TckState state, ProviderTckHarness harness, BackendControl backend) {
        super(state, harness, backend);
    }

    /**
     * Adds a typed entry to the evaluation context.
     *
     * @param key the context key
     * @param type one of {@code Boolean}, {@code String}, {@code Integer} or {@code Float}
     * @param value the value, as written in the feature file
     */
    @Given("a context containing a key {string}, with type {string} and with value {string}")
    public void contextContainingKeyWithTypeAndValue(String key, String type, String value) {
        switch (type) {
            case "Boolean":
                state.context.add(key, Boolean.parseBoolean(value));
                break;
            case "Integer":
                state.context.add(key, Integer.parseInt(value));
                break;
            case "Float":
                state.context.add(key, Double.parseDouble(value));
                break;
            case "String":
                state.context.add(key, value);
                break;
            default:
                throw new IllegalArgumentException("Unknown context value type '" + type + "'");
        }
    }

    /**
     * Adds several typed entries to the evaluation context at once.
     *
     * <p>The table form of the step above, for a scenario whose targeting depends on more than one
     * key. Its type vocabulary is {@link TckValues}', which is wider than the single-key step's --
     * {@code Long}, {@code StringList} and {@code Object} included -- because a table is where a
     * scenario reaches for the types an operator takes.
     *
     * @param table rows with {@code key}, {@code type} and {@code value} columns
     */
    @Given("a context with the following keys:")
    public void contextWithTheFollowingKeys(DataTable table) {
        // Rebuilt rather than added to: MutableContext has typed add() overloads but none taking a
        // Value, and the conversion above produces exactly that for an Object or a StringList. The
        // map carries the targeting key, so a row may set it and the old one survives when none does.
        Map<String, Value> entries = new HashMap<>(state.context.asMap());
        for (Map<String, String> row : table.asMaps(String.class, String.class)) {
            entries.put(
                    row.get("key"),
                    Value.objectToValue(TckValues.convertContextEntry(row.get("value"), row.get("type"))));
        }
        state.context = new MutableContext(entries);
    }

    /**
     * Sets the targeting key on the evaluation context.
     *
     * @param targetingKey the targeting key
     */
    @Given("a context containing a targeting key with value {string}")
    public void contextContainingTargetingKey(String targetingKey) {
        state.context.setTargetingKey(targetingKey);
    }

    /**
     * Adds a nested structure entry to the evaluation context.
     *
     * @param outerKey the outer key
     * @param innerKey the key inside the nested structure
     * @param value the string value stored under the inner key
     */
    @Given("a context containing a nested property with outer key {string} and inner key {string}, with value {string}")
    public void contextContainingNestedProperty(String outerKey, String innerKey, String value) {
        state.context.add(outerKey, new MutableStructure().add(innerKey, value));
    }
}
