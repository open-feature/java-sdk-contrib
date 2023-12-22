package dev.openfeature.contrib.providers.flagd.e2e.steps;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.parallel.Isolated;

import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.EventDetails;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.ImmutableStructure;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.Reason;
import dev.openfeature.sdk.Structure;
import dev.openfeature.sdk.Value;
import io.cucumber.java.AfterAll;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

/**
 * Common test suite used by both RPC and in-process flagd providers.
 */
@Isolated()
@Order(value = Integer.MAX_VALUE)
public class StepDefinitions {

    private static Client client;
    private static FeatureProvider provider;

    private String booleanFlagKey;
    private String stringFlagKey;
    private String intFlagKey;
    private String doubleFlagKey;
    private String objectFlagKey;

    private boolean booleanFlagDefaultValue;
    private String stringFlagDefaultValue;
    private int intFlagDefaultValue;
    private double doubleFlagDefaultValue;
    private Value objectFlagDefaultValue;

    private FlagEvaluationDetails<Value> objectFlagDetails;

    private String contextAwareFlagKey;
    private String contextAwareDefaultValue;
    private EvaluationContext context;
    private String contextAwareValue;

    private String notFoundFlagKey;
    private String notFoundDefaultValue;
    private FlagEvaluationDetails<String> notFoundDetails;
    private String typeErrorFlagKey;
    private int typeErrorDefaultValue;
    private FlagEvaluationDetails<Integer> typeErrorDetails;

    private EvaluationContext customEvaluatorContext;

    private boolean isChangeHandlerRun = false;
    private boolean isReadyHandlerRun = false;

    private Consumer<EventDetails> changeHandler;
    private Consumer<EventDetails> readyHandler;

    /**
     * Injects the client to use for this test.
     * Tests run one at a time, but just in case, a lock is used to make sure the
     * client is not updated mid-test.
     * 
     * @param client client to inject into test.
     */
    public static void setProvider(FeatureProvider provider) {
        StepDefinitions.provider = provider;
    }

    public StepDefinitions() {
        OpenFeatureAPI.getInstance().setProviderAndWait("e2e", provider);
        StepDefinitions.client = OpenFeatureAPI.getInstance().getClient("e2e");
    }

    @Given("a provider is registered")
    @Given("a flagd provider is set")
    public static void setup() {
        // done in constructor
    }

    @AfterAll()
    public static void cleanUp() throws InterruptedException {
        StepDefinitions.provider.shutdown();
        StepDefinitions.provider = null;
        StepDefinitions.client = null;
    }

    /*
     * Basic evaluation
     */

    // boolean value
    @When("a boolean flag with key {string} is evaluated with default value {string}")
    public void a_boolean_flag_with_key_boolean_flag_is_evaluated_with_default_value_false(String flagKey,
            String defaultValue) {
        this.booleanFlagKey = flagKey;
        this.booleanFlagDefaultValue = Boolean.valueOf(defaultValue);
    }

    @Then("the resolved boolean value should be {string}")
    public void the_resolved_boolean_value_should_be_true(String expected) {
        boolean value = client.getBooleanValue(this.booleanFlagKey, Boolean.valueOf(this.booleanFlagDefaultValue));
        assertEquals(Boolean.valueOf(expected), value);
    }

    // string value

    @When("a string flag with key {string} is evaluated with default value {string}")
    public void a_string_flag_with_key_is_evaluated_with_default_value(String flagKey, String defaultValue) {
        this.stringFlagKey = flagKey;
        this.stringFlagDefaultValue = defaultValue;
    }

    @Then("the resolved string value should be {string}")
    public void the_resolved_string_value_should_be(String expected) {
        String value = client.getStringValue(this.stringFlagKey, this.stringFlagDefaultValue);
        assertEquals(expected, value);
    }

    // integer value
    @When("an integer flag with key {string} is evaluated with default value {int}")
    public void an_integer_flag_with_key_is_evaluated_with_default_value(String flagKey, Integer defaultValue) {
        this.intFlagKey = flagKey;
        this.intFlagDefaultValue = defaultValue;
    }

    @Then("the resolved integer value should be {int}")
    public void the_resolved_integer_value_should_be(int expected) {
        int value = client.getIntegerValue(this.intFlagKey, this.intFlagDefaultValue);
        assertEquals(expected, value);
    }

    // float/double value
    @When("a float flag with key {string} is evaluated with default value {double}")
    public void a_float_flag_with_key_is_evaluated_with_default_value(String flagKey, double defaultValue) {
        this.doubleFlagKey = flagKey;
        this.doubleFlagDefaultValue = defaultValue;
    }

    @Then("the resolved float value should be {double}")
    public void the_resolved_float_value_should_be(double expected) {
        double value = client.getDoubleValue(this.doubleFlagKey, this.doubleFlagDefaultValue);
        assertEquals(expected, value);
    }

    // object value
    @When("an object flag with key {string} is evaluated with a null default value")
    public void an_object_flag_with_key_is_evaluated_with_a_null_default_value(String flagKey) {
        this.objectFlagKey = flagKey;
        this.objectFlagDefaultValue = new Value(); // empty value is equivalent to null
    }

    @Then("the resolved object value should be contain fields {string}, {string}, and {string}, with values {string}, {string} and {int}, respectively")
    public void the_resolved_object_value_should_be_contain_fields_and_with_values_and_respectively(String boolField,
            String stringField, String numberField, String boolValue, String stringValue, int numberValue) {
        Value value = client.getObjectValue(this.objectFlagKey, this.objectFlagDefaultValue);
        Structure structure = value.asStructure();

        assertEquals(Boolean.valueOf(boolValue), structure.asMap().get(boolField).asBoolean());
        assertEquals(stringValue, structure.asMap().get(stringField).asString());
        assertEquals(numberValue, structure.asMap().get(numberField).asInteger());
    }

    /*
     * Detailed evaluation
     */

    // boolean details
    @When("a boolean flag with key {string} is evaluated with details and default value {string}")
    public void a_boolean_flag_with_key_is_evaluated_with_details_and_default_value(String flagKey,
            String defaultValue) {
        this.booleanFlagKey = flagKey;
        this.booleanFlagDefaultValue = Boolean.valueOf(defaultValue);
    }

    @Then("the resolved boolean details value should be {string}, the variant should be {string}, and the reason should be {string}")
    public void the_resolved_boolean_value_should_be_the_variant_should_be_and_the_reason_should_be(
            String expectedValue,
            String expectedVariant, String expectedReason) {
        FlagEvaluationDetails<Boolean> details = client.getBooleanDetails(this.booleanFlagKey,
                Boolean.valueOf(this.booleanFlagDefaultValue));

        assertEquals(Boolean.valueOf(expectedValue), details.getValue());
        assertEquals(expectedVariant, details.getVariant());
        assertEquals(expectedReason, details.getReason());
    }

    // string details
    @When("a string flag with key {string} is evaluated with details and default value {string}")
    public void a_string_flag_with_key_is_evaluated_with_details_and_default_value(String flagKey,
            String defaultValue) {
        this.stringFlagKey = flagKey;
        this.stringFlagDefaultValue = defaultValue;
    }

    @Then("the resolved string details value should be {string}, the variant should be {string}, and the reason should be {string}")
    public void the_resolved_string_value_should_be_the_variant_should_be_and_the_reason_should_be(String expectedValue,
            String expectedVariant, String expectedReason) {
        FlagEvaluationDetails<String> details = client.getStringDetails(this.stringFlagKey,
                this.stringFlagDefaultValue);

        assertEquals(expectedValue, details.getValue());
        assertEquals(expectedVariant, details.getVariant());
        assertEquals(expectedReason, details.getReason());
    }

    // integer details
    @When("an integer flag with key {string} is evaluated with details and default value {int}")
    public void an_integer_flag_with_key_is_evaluated_with_details_and_default_value(String flagKey, int defaultValue) {
        this.intFlagKey = flagKey;
        this.intFlagDefaultValue = defaultValue;
    }

    @Then("the resolved integer details value should be {int}, the variant should be {string}, and the reason should be {string}")
    public void the_resolved_integer_value_should_be_the_variant_should_be_and_the_reason_should_be(int expectedValue,
            String expectedVariant, String expectedReason) {
        FlagEvaluationDetails<Integer> details = client.getIntegerDetails(this.intFlagKey, this.intFlagDefaultValue);

        assertEquals(expectedValue, details.getValue());
        assertEquals(expectedVariant, details.getVariant());
        assertEquals(expectedReason, details.getReason());
    }

    // float/double details
    @When("a float flag with key {string} is evaluated with details and default value {double}")
    public void a_float_flag_with_key_is_evaluated_with_details_and_default_value(String flagKey, double defaultValue) {
        this.doubleFlagKey = flagKey;
        this.doubleFlagDefaultValue = defaultValue;
    }

    @Then("the resolved float details value should be {double}, the variant should be {string}, and the reason should be {string}")
    public void the_resolved_float_value_should_be_the_variant_should_be_and_the_reason_should_be(double expectedValue,
            String expectedVariant, String expectedReason) {
        FlagEvaluationDetails<Double> details = client.getDoubleDetails(this.doubleFlagKey,
                this.doubleFlagDefaultValue);

        assertEquals(expectedValue, details.getValue());
        assertEquals(expectedVariant, details.getVariant());
        assertEquals(expectedReason, details.getReason());
    }

    // object details
    @When("an object flag with key {string} is evaluated with details and a null default value")
    public void an_object_flag_with_key_is_evaluated_with_details_and_a_null_default_value(String flagKey) {
        this.objectFlagKey = flagKey;
        this.objectFlagDefaultValue = new Value();
    }

    @Then("the resolved object details value should be contain fields {string}, {string}, and {string}, with values {string}, {string} and {int}, respectively")
    public void the_resolved_object_value_should_be_contain_fields_and_with_values_and_respectively_again(
            String boolField,
            String stringField, String numberField, String boolValue, String stringValue, int numberValue) {
        this.objectFlagDetails = client.getObjectDetails(this.objectFlagKey, this.objectFlagDefaultValue);
        Structure structure = this.objectFlagDetails.getValue().asStructure();

        assertEquals(Boolean.valueOf(boolValue), structure.asMap().get(boolField).asBoolean());
        assertEquals(stringValue, structure.asMap().get(stringField).asString());
        assertEquals(numberValue, structure.asMap().get(numberField).asInteger());
    }

    @Then("the variant should be {string}, and the reason should be {string}")
    public void the_variant_should_be_and_the_reason_should_be(String expectedVariant, String expectedReason) {
        assertEquals(expectedVariant, this.objectFlagDetails.getVariant());
        assertEquals(expectedReason, this.objectFlagDetails.getReason());
    }

    /*
     * Context-aware evaluation
     */

    @When("context contains keys {string}, {string}, {string}, {string} with values {string}, {string}, {int}, {string}")
    public void context_contains_keys_with_values(String field1, String field2, String field3, String field4,
            String value1, String value2, Integer value3, String value4) {
        Map<String, Value> attributes = new HashMap<>();
        attributes.put(field1, new Value(value1));
        attributes.put(field2, new Value(value2));
        attributes.put(field3, new Value(value3));
        attributes.put(field4, new Value(Boolean.valueOf(value4)));
        this.context = new ImmutableContext(attributes);
    }

    @When("a flag with key {string} is evaluated with default value {string}")
    public void an_a_flag_with_key_is_evaluated(String flagKey, String defaultValue) {
        contextAwareFlagKey = flagKey;
        contextAwareDefaultValue = defaultValue;
        contextAwareValue = client.getStringValue(flagKey, contextAwareDefaultValue, context);

    }

    @Then("the resolved string response should be {string}")
    public void the_resolved_string_response_should_be(String expected) {
        assertEquals(expected, this.contextAwareValue);
    }

    @Then("the resolved flag value is {string} when the context is empty")
    public void the_resolved_flag_value_is_when_the_context_is_empty(String expected) {
        String emptyContextValue = client.getStringValue(contextAwareFlagKey, contextAwareDefaultValue,
                new ImmutableContext());
        assertEquals(expected, emptyContextValue);
    }

    /*
     * Errors
     */

    // not found
    @When("a non-existent string flag with key {string} is evaluated with details and a default value {string}")
    public void a_non_existent_string_flag_with_key_is_evaluated_with_details_and_a_default_value(String flagKey,
            String defaultValue) {
        notFoundFlagKey = flagKey;
        notFoundDefaultValue = defaultValue;
        notFoundDetails = client.getStringDetails(notFoundFlagKey, notFoundDefaultValue);
    }

    @Then("the default string value should be returned")
    public void then_the_default_string_value_should_be_returned() {
        assertEquals(notFoundDefaultValue, notFoundDetails.getValue());
    }

    @Then("the reason should indicate an error and the error code should indicate a missing flag with {string}")
    public void the_reason_should_indicate_an_error_and_the_error_code_should_be_flag_not_found(String errorCode) {
        assertEquals(Reason.ERROR.toString(), notFoundDetails.getReason());
        assertEquals(errorCode, notFoundDetails.getErrorCode().toString());
    }

    // type mismatch
    @When("a string flag with key {string} is evaluated as an integer, with details and a default value {int}")
    public void a_string_flag_with_key_is_evaluated_as_an_integer_with_details_and_a_default_value(String flagKey,
            int defaultValue) {
        typeErrorFlagKey = flagKey;
        typeErrorDefaultValue = defaultValue;
        typeErrorDetails = client.getIntegerDetails(typeErrorFlagKey, typeErrorDefaultValue);
    }

    @Then("the default integer value should be returned")
    public void then_the_default_integer_value_should_be_returned() {
        assertEquals(typeErrorDefaultValue, typeErrorDetails.getValue());
    }

    @Then("the reason should indicate an error and the error code should indicate a type mismatch with {string}")
    public void the_reason_should_indicate_an_error_and_the_error_code_should_be_type_mismatch(String errorCode) {
        assertEquals(Reason.ERROR.toString(), typeErrorDetails.getReason());
        assertEquals(errorCode, typeErrorDetails.getErrorCode().toString());
    }

    /*
     * Custom JSON evaluators (only run for flagd-in-process)
     */

    @And("a context containing a nested property with outer key {string} and inner key {string}, with value {string}")
    public void a_context_containing_a_nested_property_with_outer_key_and_inner_key_with_value(String outerKey,
            String innerKey, String value) throws InstantiationException {
        Map<String, Value> innerMap = new HashMap<String, Value>();
        innerMap.put(innerKey, new Value(value));
        Map<String, Value> outerMap = new HashMap<String, Value>();
        outerMap.put(outerKey, new Value(new ImmutableStructure(innerMap)));
        this.customEvaluatorContext = new ImmutableContext(outerMap);
    }

    @And("a context containing a nested property with outer key {string} and inner key {string}, with value {int}")
    public void a_context_containing_a_nested_property_with_outer_key_and_inner_key_with_value_int(String outerKey,
            String innerKey, Integer value) throws InstantiationException {
        Map<String, Value> innerMap = new HashMap<String, Value>();
        innerMap.put(innerKey, new Value(value));
        Map<String, Value> outerMap = new HashMap<String, Value>();
        outerMap.put(outerKey, new Value(new ImmutableStructure(innerMap)));
        this.customEvaluatorContext = new ImmutableContext(outerMap);
    }


    @And("a context containing a key {string}, with value {string}")
    public void a_context_containing_a_key_with_value(String key, String value) {
        Map<String, Value> attrs = new HashMap<String, Value>();
        attrs.put(key, new Value(value));
        this.customEvaluatorContext = new ImmutableContext(attrs);
    }

    @And("a context containing a key {string}, with value {double}")
    public void a_context_containing_a_key_with_value_double(String key, Double value) {
        Map<String, Value> attrs = new HashMap<String, Value>();
        attrs.put(key, new Value(value));
        this.customEvaluatorContext = new ImmutableContext(attrs);
    }

    @Then("the returned value should be {string}")
    public void the_returned_value_should_be(String expected) {
        String value = client.getStringValue(this.stringFlagKey, this.stringFlagDefaultValue,
                this.customEvaluatorContext);
        assertEquals(expected, value);
    }

    @Then("the returned value should be {int}")
    public void the_returned_value_should_be(Integer expectedValue) {
        Integer value = client.getIntegerValue(this.intFlagKey, this.intFlagDefaultValue,
                this.customEvaluatorContext);
        assertEquals(expectedValue, value);
    }

    /*
     * Events
     */

    // Flag change event
    @When("a PROVIDER_CONFIGURATION_CHANGED handler is added")
    public void a_provider_configuration_changed_handler_is_added() {
        this.changeHandler = (EventDetails details) -> {
            this.isChangeHandlerRun = true;
        };
        client.onProviderConfigurationChanged(this.changeHandler);

    }

    @When("a flag with key {string} is modified")
    public void a_flag_with_key_is_modified(String flagKey) {
        // This happens automatically
    }

    @Then("the PROVIDER_CONFIGURATION_CHANGED handler must run")
    public void the_provider_configuration_changed_handler_must_run() {
        Awaitility.await()
                .atMost(Duration.ofSeconds(2))
                .until(() -> {
                    return this.isChangeHandlerRun;
                });
    }

    @Then("the event details must indicate {string} was altered")
    public void the_event_details_must_indicate_was_altered(String flagKey) {
        // TODO: In-process-provider doesnt support flag change list.
    }

    // Provider ready event
    @When("a PROVIDER_READY handler is added")
    public void a_provider_ready_handler_is_added() {
        this.readyHandler = (EventDetails details) -> {
            this.isReadyHandlerRun = true;
        };
        client.onProviderReady(this.readyHandler);
    }

    @Then("the PROVIDER_READY handler must run")
    public void the_provider_ready_handler_must_run() {
        Awaitility.await()
                .atMost(Duration.ofSeconds(2))
                .until(() -> {
                    return this.isReadyHandlerRun;
                });
    }

    /*
     * Zero Value
     */

    // boolean value
    @When("a zero-value boolean flag with key {string} is evaluated with default value {string}")
    public void a_zero_value_boolean_flag_with_key_is_evaluated_with_default_value(String flagKey,
            String defaultValue) {
        this.booleanFlagKey = flagKey;
        this.booleanFlagDefaultValue = Boolean.valueOf(defaultValue);
    }

    @Then("the resolved boolean zero-value should be {string}")
    public void the_resolved_boolean_zero_value_should_be(String expected) {
        boolean value = client.getBooleanValue(this.booleanFlagKey, this.booleanFlagDefaultValue);
        assertEquals(Boolean.valueOf(expected), value);
    }

    // float/double value
    @When("a zero-value float flag with key {string} is evaluated with default value {double}")
    public void a_zero_value_float_flag_with_key_is_evaluated_with_default_value(String flagKey, Double defaultValue) {
        this.doubleFlagKey = flagKey;
        this.doubleFlagDefaultValue = defaultValue;
    }

    @Then("the resolved float zero-value should be {double}")
    public void the_resolved_float_zero_value_should_be(Double expected) {
        FlagEvaluationDetails<Double> details =
        client.getDoubleDetails("float-zero-flag", this.doubleFlagDefaultValue);
        assertEquals(expected, details.getValue());
    }

    // integer value
    @When("a zero-value integer flag with key {string} is evaluated with default value {int}")
    public void a_zero_value_integer_flag_with_key_is_evaluated_with_default_value(String flagKey,
            Integer defaultValue) {
        this.intFlagKey = flagKey;
        this.intFlagDefaultValue = defaultValue;
    }

    @Then("the resolved integer zero-value should be {int}")
    public void the_resolved_integer_zero_value_should_be(Integer expected) {
        int value = client.getIntegerValue(this.intFlagKey, this.intFlagDefaultValue);
        assertEquals(expected, value);
    }

    // string value
    @When("a zero-value string flag with key {string} is evaluated with default value {string}")
    public void a_zero_value_string_flag_with_key_is_evaluated_with_default_value(String flagKey, String defaultValue) {
        this.stringFlagKey = flagKey;
        this.stringFlagDefaultValue = defaultValue;
    }

    @Then("the resolved string zero-value should be {string}")
    public void the_resolved_string_zero_value_should_be(String expected) {
        String value = client.getStringValue(this.stringFlagKey, this.stringFlagDefaultValue);
        assertEquals(expected, value);
    }
}