package dev.openfeature.contrib.tools.providertck.steps;

import dev.openfeature.contrib.tools.providertck.TckState;
import dev.openfeature.sdk.MutableStructure;
import io.cucumber.java.en.Given;

/**
 * Steps that build the evaluation context passed to the evaluation call.
 *
 * <p>Step vocabulary is inherited verbatim from the flagd test harness.
 *
 * <p>Note the TCK cannot currently assert that the context <em>reached</em> the backend intact.
 * Doing so needs an echo operation on the control API — something like
 * {@code GET /last-evaluation} returning the request the backend last received — which the control
 * API does not yet define. Context passthrough is therefore a known gap rather than a covered case.
 */
public class ContextSteps extends AbstractSteps {

    public ContextSteps(TckState state) {
        super(state);
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
