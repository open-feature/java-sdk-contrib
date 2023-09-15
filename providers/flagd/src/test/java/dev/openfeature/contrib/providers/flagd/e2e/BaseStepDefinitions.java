package dev.openfeature.contrib.providers.flagd.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;

import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.Reason;
import dev.openfeature.sdk.Structure;
import dev.openfeature.sdk.Value;

public class BaseStepDefinitions {

    protected static Client client;

    private boolean booleanFlagValue;
    private String stringFlagValue;
    private int intFlagValue;
    private double doubleFlagValue;
    private Value objectFlagValue;

    private FlagEvaluationDetails<Boolean> booleanFlagDetails;
    private FlagEvaluationDetails<String> stringFlagDetails;
    private FlagEvaluationDetails<Integer> intFlagDetails;
    private FlagEvaluationDetails<Double> doubleFlagDetails;
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

    /*
     * Basic evaluation
     */

    // boolean value
    public void a_boolean_flag_with_key_boolean_flag_is_evaluated_with_default_value_false(String flagKey,
            String defaultValue) {
        this.booleanFlagValue = client.getBooleanValue(flagKey, Boolean.valueOf(defaultValue));
    }

    public void the_resolved_boolean_value_should_be_true(String expected) {
        assertEquals(Boolean.valueOf(expected), this.booleanFlagValue);
    }

    // string value
    public void a_string_flag_with_key_is_evaluated_with_default_value(String flagKey, String defaultValue) {
        this.stringFlagValue = client.getStringValue(flagKey, defaultValue);
    }

    public void the_resolved_string_value_should_be(String expected) {
        assertEquals(expected, this.stringFlagValue);
    }

    // integer value
    public void an_integer_flag_with_key_is_evaluated_with_default_value(String flagKey, Integer defaultValue) {
        this.intFlagValue = client.getIntegerValue(flagKey, defaultValue);
    }

    public void the_resolved_integer_value_should_be(int expected) {
        assertEquals(expected, this.intFlagValue);
    }

    // float/double value
    public void a_float_flag_with_key_is_evaluated_with_default_value(String flagKey, double defaultValue) {
        this.doubleFlagValue = client.getDoubleValue(flagKey, defaultValue);
    }

    public void the_resolved_float_value_should_be(double expected) {
        assertEquals(expected, this.doubleFlagValue);
    }

    // object value
    public void an_object_flag_with_key_is_evaluated_with_a_null_default_value(String flagKey) {
        this.objectFlagValue = client.getObjectValue(flagKey, new Value());
    }

    public void the_resolved_object_value_should_be_contain_fields_and_with_values_and_respectively(String boolField,
            String stringField, String numberField, String boolValue, String stringValue, int numberValue) {
        Structure structure = this.objectFlagValue.asStructure();

        assertEquals(Boolean.valueOf(boolValue), structure.asMap().get(boolField).asBoolean());
        assertEquals(stringValue, structure.asMap().get(stringField).asString());
        assertEquals(numberValue, structure.asMap().get(numberField).asInteger());
    }

    /*
     * Detailed evaluation
     */

    // boolean details
    public void a_boolean_flag_with_key_is_evaluated_with_details_and_default_value(String flagKey,
            String defaultValue) {
        this.booleanFlagDetails = client.getBooleanDetails(flagKey, Boolean.valueOf(defaultValue));
    }

    public void the_resolved_boolean_value_should_be_the_variant_should_be_and_the_reason_should_be(
            String expectedValue,
            String expectedVariant, String expectedReason) {
        assertEquals(Boolean.valueOf(expectedValue), booleanFlagDetails.getValue());
        assertEquals(expectedVariant, booleanFlagDetails.getVariant());
        assertEquals(expectedReason, booleanFlagDetails.getReason());
    }

    // string details
    public void a_string_flag_with_key_is_evaluated_with_details_and_default_value(String flagKey,
            String defaultValue) {
        this.stringFlagDetails = client.getStringDetails(flagKey, defaultValue);
    }

    public void the_resolved_string_value_should_be_the_variant_should_be_and_the_reason_should_be(String expectedValue,
            String expectedVariant, String expectedReason) {
        assertEquals(expectedValue, this.stringFlagDetails.getValue());
        assertEquals(expectedVariant, this.stringFlagDetails.getVariant());
        assertEquals(expectedReason, this.stringFlagDetails.getReason());
    }

    // integer details
    public void an_integer_flag_with_key_is_evaluated_with_details_and_default_value(String flagKey, int defaultValue) {
        this.intFlagDetails = client.getIntegerDetails(flagKey, defaultValue);
    }

    public void the_resolved_integer_value_should_be_the_variant_should_be_and_the_reason_should_be(int expectedValue,
            String expectedVariant, String expectedReason) {
        assertEquals(expectedValue, this.intFlagDetails.getValue());
        assertEquals(expectedVariant, this.intFlagDetails.getVariant());
        assertEquals(expectedReason, this.intFlagDetails.getReason());
    }

    // float/double details
    public void a_float_flag_with_key_is_evaluated_with_details_and_default_value(String flagKey, double defaultValue) {
        this.doubleFlagDetails = client.getDoubleDetails(flagKey, defaultValue);
    }

    public void the_resolved_float_value_should_be_the_variant_should_be_and_the_reason_should_be(double expectedValue,
            String expectedVariant, String expectedReason) {
        assertEquals(expectedValue, this.doubleFlagDetails.getValue());
        assertEquals(expectedVariant, this.doubleFlagDetails.getVariant());
        assertEquals(expectedReason, this.doubleFlagDetails.getReason());
    }

    // object details
    public void an_object_flag_with_key_is_evaluated_with_details_and_a_null_default_value(String flagKey) {
        this.objectFlagDetails = client.getObjectDetails(flagKey, new Value());
    }

    public void the_resolved_object_value_should_be_contain_fields_and_with_values_and_respectively_again(
            String boolField,
            String stringField, String numberField, String boolValue, String stringValue, int numberValue) {
        Structure structure = this.objectFlagDetails.getValue().asStructure();

        assertEquals(Boolean.valueOf(boolValue), structure.asMap().get(boolField).asBoolean());
        assertEquals(stringValue, structure.asMap().get(stringField).asString());
        assertEquals(numberValue, structure.asMap().get(numberField).asInteger());
    }

    public void the_variant_should_be_and_the_reason_should_be(String expectedVariant, String expectedReason) {
        assertEquals(expectedVariant, this.objectFlagDetails.getVariant());
        assertEquals(expectedReason, this.objectFlagDetails.getReason());
    }

    /*
     * Context-aware evaluation
     */

    public void context_contains_keys_with_values(String field1, String field2, String field3, String field4,
            String value1, String value2, Integer value3, String value4) {
        Map<String, Value> attributes = new HashMap<>();
        attributes.put(field1, new Value(value1));
        attributes.put(field2, new Value(value2));
        attributes.put(field3, new Value(value3));
        attributes.put(field4, new Value(Boolean.valueOf(value4)));
        this.context = new ImmutableContext(attributes);
    }

    public void an_a_flag_with_key_is_evaluated(String flagKey, String defaultValue) {
        contextAwareFlagKey = flagKey;
        contextAwareDefaultValue = defaultValue;
        contextAwareValue = client.getStringValue(flagKey, contextAwareDefaultValue, context);

    }

    public void the_resolved_string_response_should_be(String expected) {
        assertEquals(expected, this.contextAwareValue);
    }

    public void the_resolved_flag_value_is_when_the_context_is_empty(String expected) {
        String emptyContextValue = client.getStringValue(contextAwareFlagKey, contextAwareDefaultValue,
                new ImmutableContext());
        assertEquals(expected, emptyContextValue);
    }

    /*
     * Errors
     */

    // not found
    public void a_non_existent_string_flag_with_key_is_evaluated_with_details_and_a_default_value(String flagKey,
            String defaultValue) {
        notFoundFlagKey = flagKey;
        notFoundDefaultValue = defaultValue;
        notFoundDetails = client.getStringDetails(notFoundFlagKey, notFoundDefaultValue);
    }

    public void then_the_default_string_value_should_be_returned() {
        assertEquals(notFoundDefaultValue, notFoundDetails.getValue());
    }

    public void the_reason_should_indicate_an_error_and_the_error_code_should_be_flag_not_found(String errorCode) {
        assertEquals(Reason.ERROR.toString(), notFoundDetails.getReason());
        assertEquals(errorCode, notFoundDetails.getErrorCode().toString());
        // TODO: add errorCode assertion once flagd provider is updated.
    }

    // type mismatch
    public void a_string_flag_with_key_is_evaluated_as_an_integer_with_details_and_a_default_value(String flagKey,
            int defaultValue) {
        typeErrorFlagKey = flagKey;
        typeErrorDefaultValue = defaultValue;
        typeErrorDetails = client.getIntegerDetails(typeErrorFlagKey, typeErrorDefaultValue);
    }

    public void then_the_default_integer_value_should_be_returned() {
        assertEquals(typeErrorDefaultValue, typeErrorDetails.getValue());
    }

    public void the_reason_should_indicate_an_error_and_the_error_code_should_be_type_mismatch(String errorCode) {
        assertEquals(Reason.ERROR.toString(), typeErrorDetails.getReason());
        assertEquals(errorCode, typeErrorDetails.getErrorCode().toString());
        // TODO: add errorCode assertion once flagd provider is updated.
    }

}
