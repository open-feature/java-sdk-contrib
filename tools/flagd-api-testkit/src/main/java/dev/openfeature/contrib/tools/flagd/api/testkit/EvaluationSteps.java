package dev.openfeature.contrib.tools.flagd.api.testkit;

import static org.assertj.core.api.Assertions.assertThat;

import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.ImmutableMetadata;
import dev.openfeature.sdk.Value;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Cucumber step definitions for evaluating flags via the
 * {@link dev.openfeature.contrib.tools.flagd.api.Evaluator} interface.
 *
 * <p>These steps cover: flag setup, evaluation dispatch, and assertions on value, reason,
 * variant, error code, and flag metadata. Consumers only need to implement the
 * {@code Given an evaluator} step.
 */
@Slf4j
public class EvaluationSteps {

    private final EvaluatorState state;

    /** Constructs steps with shared scenario state injected by PicoContainer. */
    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "Intentional mutable state sharing required by Cucumber PicoContainer DI")
    public EvaluationSteps(EvaluatorState state) {
        this.state = state;
    }

    /** Registers the flag key, type and default value for the current scenario. */
    @Given("a {}-flag with key {string} and a fallback value {string}")
    public void givenAFlag(String type, String key, String defaultValue) throws Throwable {
        state.flagType = type;
        state.flagKey = key;
        state.defaultValue = EvaluatorUtils.convert(defaultValue, type);
    }

    /** Evaluates the registered flag via the {@code Evaluator} and stores the result. */
    @When("the flag was evaluated with details")
    public void flagEvaluatedWithDetails() {
        try {
            switch (state.flagType) {
                case "Boolean":
                    state.evaluation = state.getEvaluator()
                            .resolveBooleanValue(state.flagKey, (Boolean) state.defaultValue, state.context);
                    break;
                case "String":
                    state.evaluation = state.getEvaluator()
                            .resolveStringValue(state.flagKey, (String) state.defaultValue, state.context);
                    break;
                case "Integer":
                    state.evaluation = state.getEvaluator()
                            .resolveIntegerValue(state.flagKey, (Integer) state.defaultValue, state.context);
                    break;
                case "Float":
                    state.evaluation = state.getEvaluator()
                            .resolveDoubleValue(state.flagKey, (Double) state.defaultValue, state.context);
                    break;
                case "Object":
                    state.evaluation = state.getEvaluator()
                            .resolveObjectValue(state.flagKey, (Value) state.defaultValue, state.context);
                    break;
                default:
                    throw new AssertionError("Unknown flag type: " + state.flagType);
            }
        } catch (dev.openfeature.sdk.exceptions.TypeMismatchError e) {
            state.evaluation = dev.openfeature.sdk.ProviderEvaluation.builder()
                    .errorCode(ErrorCode.TYPE_MISMATCH)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    /** Asserts the resolved flag value equals the expected string (converted to the flag type). */
    @Then("the resolved details value should be {string}")
    public void resolvedValueEquals(String value) throws Throwable {
        if (state.evaluation.getErrorCode() != null) {
            log.warn("Evaluation error: {}", state.evaluation.getErrorMessage());
        }
        assertThat(state.evaluation.getValue()).isEqualTo(EvaluatorUtils.convert(value, state.flagType));
    }

    /** Asserts the evaluation reason matches the expected value. */
    @Then("the reason should be {string}")
    public void reasonEquals(String reason) {
        assertThat(state.evaluation.getReason()).isEqualTo(reason);
    }

    /** Asserts the resolved variant matches the expected value. */
    @Then("the variant should be {string}")
    public void variantEquals(String variant) {
        assertThat(state.evaluation.getVariant()).isEqualTo(variant);
    }

    /** Asserts the error code matches the expected value, or is absent when the expected value is empty. */
    @Then("the error-code should be {string}")
    public void errorCodeEquals(String errorCode) {
        if (errorCode.isEmpty()) {
            assertThat(state.evaluation.getErrorCode()).isNull();
        } else {
            assertThat(state.evaluation.getErrorCode()).isEqualTo(ErrorCode.valueOf(errorCode));
        }
    }

    /** Asserts the resolved flag metadata map is empty. */
    @Then("the resolved metadata is empty")
    @SuppressWarnings("unchecked")
    public void resolvedMetadataIsEmpty() throws NoSuchFieldException, IllegalAccessException {
        ImmutableMetadata flagMetadata = state.evaluation.getFlagMetadata();
        Field metadataField = flagMetadata.getClass().getDeclaredField("metadata");
        metadataField.setAccessible(true);
        Map<String, Object> metadataMap = (Map<String, Object>) metadataField.get(flagMetadata);
        assertThat(metadataMap).isEmpty();
    }

    /** Asserts that the resolved flag metadata contains the key/value pairs from the data table. */
    @Then("the resolved metadata should contain")
    @SuppressWarnings("unchecked")
    public void resolvedMetadataContains(DataTable dataTable) throws IOException, ClassNotFoundException {
        ImmutableMetadata flagMetadata = state.evaluation.getFlagMetadata();
        List<Map<String, String>> rows = dataTable.asMaps(String.class, String.class);
        for (Map<String, String> row : rows) {
            switch (row.get("metadata_type")) {
                case "String":
                    assertThat(flagMetadata.getString(row.get("key")))
                            .isEqualTo(EvaluatorUtils.convert(row.get("value"), row.get("metadata_type")));
                    break;
                case "Boolean":
                    assertThat(flagMetadata.getBoolean(row.get("key")))
                            .isEqualTo(EvaluatorUtils.convert(row.get("value"), row.get("metadata_type")));
                    break;
                case "Float":
                    assertThat(flagMetadata.getDouble(row.get("key")))
                            .isEqualTo(EvaluatorUtils.convert(row.get("value"), row.get("metadata_type")));
                    break;
                case "Integer":
                    assertThat(flagMetadata.getInteger(row.get("key")))
                            .isEqualTo(EvaluatorUtils.convert(row.get("value"), row.get("metadata_type")));
                    break;
                default:
                    throw new AssertionError("Unsupported metadata type: " + row.get("metadata_type"));
            }
        }
    }
}
