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
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/** Cucumber step definitions for evaluating flags via the
 * {@link dev.openfeature.contrib.tools.flagd.api.Evaluator} interface.
 *
 * <p>These steps cover: flag setup, evaluation dispatch, and assertions on value, reason,
 * variant, error code, and flag metadata. Consumers only need to implement the
 * {@code Given an evaluator} step.
 */
public class EvaluationSteps {

    private static final Logger log = Logger.getLogger(EvaluationSteps.class.getName());

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
    public void givenAFlag(String type, String key, String defaultValue) throws IOException {
        state.flagType = FlagType.fromString(type);
        state.flagKey = key;
        state.defaultValue = EvaluatorUtils.convert(defaultValue, state.flagType);
    }

    /** Evaluates the registered flag via the {@code Evaluator} and stores the result. */
    @When("the flag was evaluated with details")
    public void flagEvaluatedWithDetails() {
        try {
            switch (state.flagType) {
                case BOOLEAN:
                    state.evaluation = state.getEvaluator()
                            .resolveBooleanValue(state.flagKey, (Boolean) state.defaultValue, state.context);
                    break;
                case STRING:
                    state.evaluation = state.getEvaluator()
                            .resolveStringValue(state.flagKey, (String) state.defaultValue, state.context);
                    break;
                case INTEGER:
                    state.evaluation = state.getEvaluator()
                            .resolveIntegerValue(state.flagKey, (Integer) state.defaultValue, state.context);
                    break;
                case FLOAT:
                    state.evaluation = state.getEvaluator()
                            .resolveDoubleValue(state.flagKey, (Double) state.defaultValue, state.context);
                    break;
                case OBJECT:
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
        } catch (dev.openfeature.sdk.exceptions.OpenFeatureError e) {
            // Mirror the OpenFeature SDK client behaviour: on any provider error, return the
            // caller-supplied default value together with the error code and message.
            state.evaluation = dev.openfeature.sdk.ProviderEvaluation.builder()
                    .value(state.defaultValue)
                    .errorCode(e.getErrorCode())
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    /** Asserts the resolved flag value equals the expected string (converted to the flag type). */
    @Then("the resolved details value should be {string}")
    public void resolvedValueEquals(String value) throws IOException {
        if (state.evaluation.getErrorCode() != null) {
            log.warning("Evaluation error: " + state.evaluation.getErrorMessage());
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
    public void resolvedMetadataIsEmpty() {
        ImmutableMetadata flagMetadata = state.evaluation.getFlagMetadata();
        assertThat(flagMetadata.asUnmodifiableMap()).isEmpty();
    }

    /** Asserts that the resolved flag metadata contains the key/value pairs from the data table. */
    @Then("the resolved metadata should contain")
    public void resolvedMetadataContains(DataTable dataTable) throws IOException {
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
