package dev.openfeature.contrib.tools.tck.internal.steps;

import dev.openfeature.contrib.tools.tck.BackendControl;
import dev.openfeature.contrib.tools.tck.ProviderTckHarness;
import dev.openfeature.contrib.tools.tck.TckState;
import dev.openfeature.sdk.MutableStructure;
import io.cucumber.java.en.Given;

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
