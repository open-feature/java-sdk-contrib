package dev.openfeature.contrib.providers.flagd.e2e.rpc;

import dev.openfeature.contrib.providers.flagd.Config;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.FlagdProvider;
import dev.openfeature.contrib.providers.flagd.e2e.BaseStepDefinitions;
import dev.openfeature.sdk.OpenFeatureAPI;
import io.cucumber.java.BeforeAll;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

public class FlagdRpcStepDefinitions extends BaseStepDefinitions {

    @BeforeAll()
    @Given("a provider is registered")
    public static void setup() {
        if (client == null) {
                FlagdProvider provider = new FlagdProvider(FlagdOptions.builder()
                .resolverType(Config.Evaluator.RPC)
                // set a generous deadline, to prevent timeouts in actions
                .deadline(3000)
                .build());
                OpenFeatureAPI.getInstance().setProvider(provider);
                client = OpenFeatureAPI.getInstance().getClient();
        }
    }

    /*
     * Basic evaluation
     */
    
    // boolean value
    @When("a boolean flag with key {string} is evaluated with default value {string}")
    public void a_boolean_flag_with_key_boolean_flag_is_evaluated_with_default_value_false(String flagKey,
            String defaultValue) {
        super.a_boolean_flag_with_key_boolean_flag_is_evaluated_with_default_value_false(flagKey, defaultValue);
    }

    @Then("the resolved boolean value should be {string}")
    public void the_resolved_boolean_value_should_be_true(String expected) {
        super.the_resolved_boolean_value_should_be_true(expected);
    }

    // string value
    @When("a string flag with key {string} is evaluated with default value {string}")
    public void a_string_flag_with_key_is_evaluated_with_default_value(String flagKey, String defaultValue) {
        super.a_string_flag_with_key_is_evaluated_with_default_value(flagKey, defaultValue);
    }

    @Then("the resolved string value should be {string}")
    public void the_resolved_string_value_should_be(String expected) {
        super.the_resolved_string_value_should_be(expected);
    }

    // integer value
    @When("an integer flag with key {string} is evaluated with default value {int}")
    public void an_integer_flag_with_key_is_evaluated_with_default_value(String flagKey, Integer defaultValue) {
        super.an_integer_flag_with_key_is_evaluated_with_default_value(flagKey, defaultValue);
    }

    @Then("the resolved integer value should be {int}")
    public void the_resolved_integer_value_should_be(int expected) {
        super.the_resolved_integer_value_should_be(expected);
    }

    // float/double value
    @When("a float flag with key {string} is evaluated with default value {double}")
    public void a_float_flag_with_key_is_evaluated_with_default_value(String flagKey, double defaultValue) {
        super.a_float_flag_with_key_is_evaluated_with_default_value(flagKey, defaultValue);
    }

    @Then("the resolved float value should be {double}")
    public void the_resolved_float_value_should_be(double expected) {
        super.the_resolved_float_value_should_be(expected);
    }

    // object value
    @When("an object flag with key {string} is evaluated with a null default value")
    public void an_object_flag_with_key_is_evaluated_with_a_null_default_value(String flagKey) {
        super.an_object_flag_with_key_is_evaluated_with_a_null_default_value(flagKey);
    }

    @Then("the resolved object value should be contain fields {string}, {string}, and {string}, with values {string}, {string} and {int}, respectively")
    public void the_resolved_object_value_should_be_contain_fields_and_with_values_and_respectively(String boolField,
            String stringField, String numberField, String boolValue, String stringValue, int numberValue) {
        super.the_resolved_object_value_should_be_contain_fields_and_with_values_and_respectively(boolField, stringField, numberField, boolValue, stringValue, numberValue);
    }

    /*
     * Detailed evaluation
     */

    // boolean details
    @When("a boolean flag with key {string} is evaluated with details and default value {string}")
    public void a_boolean_flag_with_key_is_evaluated_with_details_and_default_value(String flagKey,
            String defaultValue) {
       super.a_boolean_flag_with_key_is_evaluated_with_details_and_default_value(flagKey, defaultValue);
    }

    @Then("the resolved boolean details value should be {string}, the variant should be {string}, and the reason should be {string}")
    public void the_resolved_boolean_value_should_be_the_variant_should_be_and_the_reason_should_be(
            String expectedValue,
            String expectedVariant, String expectedReason) {
        super.the_resolved_boolean_value_should_be_the_variant_should_be_and_the_reason_should_be(expectedValue, expectedVariant, expectedReason);
    }

    // string details
    @When("a string flag with key {string} is evaluated with details and default value {string}")
    public void a_string_flag_with_key_is_evaluated_with_details_and_default_value(String flagKey,
            String defaultValue) {
        super.a_string_flag_with_key_is_evaluated_with_details_and_default_value(flagKey, defaultValue);
    }

    @Then("the resolved string details value should be {string}, the variant should be {string}, and the reason should be {string}")
    public void the_resolved_string_value_should_be_the_variant_should_be_and_the_reason_should_be(String expectedValue,
            String expectedVariant, String expectedReason) {
                super.the_resolved_string_value_should_be_the_variant_should_be_and_the_reason_should_be(expectedValue, expectedVariant, expectedReason);
    }

    // integer details
    @When("an integer flag with key {string} is evaluated with details and default value {int}")
    public void an_integer_flag_with_key_is_evaluated_with_details_and_default_value(String flagKey, int defaultValue) {
       super.an_integer_flag_with_key_is_evaluated_with_details_and_default_value(flagKey, defaultValue);
    }

    @Then("the resolved integer details value should be {int}, the variant should be {string}, and the reason should be {string}")
    public void the_resolved_integer_value_should_be_the_variant_should_be_and_the_reason_should_be(int expectedValue,
            String expectedVariant, String expectedReason) {
        super.the_resolved_integer_value_should_be_the_variant_should_be_and_the_reason_should_be(expectedValue, expectedVariant, expectedReason);
    }

    // float/double details
    @When("a float flag with key {string} is evaluated with details and default value {double}")
    public void a_float_flag_with_key_is_evaluated_with_details_and_default_value(String flagKey, double defaultValue) {
        super.a_float_flag_with_key_is_evaluated_with_details_and_default_value(flagKey, defaultValue);
    }

    @Then("the resolved float details value should be {double}, the variant should be {string}, and the reason should be {string}")
    public void the_resolved_float_value_should_be_the_variant_should_be_and_the_reason_should_be(double expectedValue,
            String expectedVariant, String expectedReason) {
        super.the_resolved_float_value_should_be_the_variant_should_be_and_the_reason_should_be(expectedValue, expectedVariant, expectedReason);
    }

    // object details
    @When("an object flag with key {string} is evaluated with details and a null default value")
    public void an_object_flag_with_key_is_evaluated_with_details_and_a_null_default_value(String flagKey) {
        super.an_object_flag_with_key_is_evaluated_with_details_and_a_null_default_value(flagKey);
    }

    @Then("the resolved object details value should be contain fields {string}, {string}, and {string}, with values {string}, {string} and {int}, respectively")
    public void the_resolved_object_value_should_be_contain_fields_and_with_values_and_respectively_again(
            String boolField,
            String stringField, String numberField, String boolValue, String stringValue, int numberValue) {
        super.the_resolved_object_value_should_be_contain_fields_and_with_values_and_respectively_again(boolField, stringField, numberField, boolValue, stringValue, numberValue);
        
    }

    @Then("the variant should be {string}, and the reason should be {string}")
    public void the_variant_should_be_and_the_reason_should_be(String expectedVariant, String expectedReason) {
        super.the_variant_should_be_and_the_reason_should_be(expectedVariant, expectedReason);
    }

    /*
     * Context-aware evaluation
     */

    @When("context contains keys {string}, {string}, {string}, {string} with values {string}, {string}, {int}, {string}")
    public void context_contains_keys_with_values(String field1, String field2, String field3, String field4,
            String value1, String value2, Integer value3, String value4) {
        super.context_contains_keys_with_values(field1, field2, field3, field4, value1, value2, value3, value4);
    }

    @When("a flag with key {string} is evaluated with default value {string}")
    public void an_a_flag_with_key_is_evaluated(String flagKey, String defaultValue) {
        super.an_a_flag_with_key_is_evaluated(flagKey, defaultValue);
    }

    @Then("the resolved string response should be {string}")
    public void the_resolved_string_response_should_be(String expected) {
        super.the_resolved_string_response_should_be(expected);
    }

    @Then("the resolved flag value is {string} when the context is empty")
    public void the_resolved_flag_value_is_when_the_context_is_empty(String expected) {
        super.the_resolved_flag_value_is_when_the_context_is_empty(expected);
    }

    /*
     * Errors
     */

    // not found
    @When("a non-existent string flag with key {string} is evaluated with details and a default value {string}")
    public void a_non_existent_string_flag_with_key_is_evaluated_with_details_and_a_default_value(String flagKey,
            String defaultValue) {
        super.a_non_existent_string_flag_with_key_is_evaluated_with_details_and_a_default_value(flagKey, defaultValue);
    }

    @Then("the default string value should be returned")
    public void then_the_default_string_value_should_be_returned() {
        super.then_the_default_string_value_should_be_returned();
    }

    @Override
    @Then("the reason should indicate an error and the error code should indicate a missing flag with {string}")
    public void the_reason_should_indicate_an_error_and_the_error_code_should_be_flag_not_found(String errorCode) {
        super.the_reason_should_indicate_an_error_and_the_error_code_should_be_flag_not_found(errorCode);
    }

    // type mismatch
    @When("a string flag with key {string} is evaluated as an integer, with details and a default value {int}")
    public void a_string_flag_with_key_is_evaluated_as_an_integer_with_details_and_a_default_value(String flagKey,
            int defaultValue) {
                super.a_string_flag_with_key_is_evaluated_as_an_integer_with_details_and_a_default_value(flagKey, defaultValue);
    }

    @Then("the default integer value should be returned")
    public void then_the_default_integer_value_should_be_returned() {
        super.then_the_default_integer_value_should_be_returned();
    }

    @Then("the reason should indicate an error and the error code should indicate a type mismatch with {string}")
    public void the_reason_should_indicate_an_error_and_the_error_code_should_be_type_mismatch(String errorCode) {
        super.the_reason_should_indicate_an_error_and_the_error_code_should_be_type_mismatch(errorCode);
    }
}
