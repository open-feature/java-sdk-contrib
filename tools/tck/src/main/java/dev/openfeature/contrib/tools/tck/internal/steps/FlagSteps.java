package dev.openfeature.contrib.tools.tck.internal.steps;

import static org.assertj.core.api.Assertions.assertThat;

import dev.openfeature.contrib.tools.tck.BackendControl;
import dev.openfeature.contrib.tools.tck.FlagUnderTest;
import dev.openfeature.contrib.tools.tck.ProviderEventRecord;
import dev.openfeature.contrib.tools.tck.ProviderTckHarness;
import dev.openfeature.contrib.tools.tck.TckState;
import dev.openfeature.contrib.tools.tck.internal.TckValues;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.ImmutableMetadata;
import dev.openfeature.sdk.Structure;
import dev.openfeature.sdk.Value;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Flag evaluation steps and assertions on resolution details.
 */
public class FlagSteps extends AbstractSteps {

    private static final Logger log = LoggerFactory.getLogger(FlagSteps.class);

    public FlagSteps(TckState state, ProviderTckHarness harness, BackendControl backend) {
        super(state, harness, backend);
    }

    /**
     * Declares the flag the scenario will evaluate.
     *
     * @param type the declared type: {@code Boolean}, {@code String}, {@code Integer},
     *     {@code Float} or {@code Object}
     * @param key the flag key
     * @param defaultValue the code default, as written in the feature file
     */
    @Given("a {}-flag with key {string} and a default value {string}")
    public void flagWithKeyAndDefaultValue(String type, String key, String defaultValue) {
        state.flag = new FlagUnderTest(key, type, TckValues.convert(defaultValue, type));
    }

    /**
     * Evaluates the declared flag via the matching typed API, recording any exception.
     */
    @When("the flag was evaluated with details")
    public void theFlagWasEvaluatedWithDetails() {
        FlagUnderTest flag = state.flag;
        try {
            switch (flag.type()) {
                case "Boolean":
                    state.evaluation =
                            state.client.getBooleanDetails(flag.key(), (Boolean) flag.defaultValue(), state.context);
                    break;
                case "String":
                    state.evaluation =
                            state.client.getStringDetails(flag.key(), (String) flag.defaultValue(), state.context);
                    break;
                case "Integer":
                    state.evaluation =
                            state.client.getIntegerDetails(flag.key(), (Integer) flag.defaultValue(), state.context);
                    break;
                case "Float":
                    state.evaluation =
                            state.client.getDoubleDetails(flag.key(), (Double) flag.defaultValue(), state.context);
                    break;
                case "Object":
                    state.evaluation =
                            state.client.getObjectDetails(flag.key(), (Value) flag.defaultValue(), state.context);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown flag type '" + flag.type() + "'");
            }
        } catch (RuntimeException e) {
            log.warn("Evaluation of '{}' threw, which violates the SDK contract", flag.key(), e);
            state.thrown = e;
            state.thrownBy = "evaluation of '" + flag.key() + "'";
        }
    }

    /**
     * Asserts the resolved value, converted according to the flag's declared type.
     *
     * @param value the expected value, as written in the feature file
     */
    @Then("the resolved details value should be \"{}\"")
    public void theResolvedDetailsValueShouldBe(String value) {
        requireEvaluation();
        if (state.evaluation.getErrorCode() != null) {
            log.info(
                    "Evaluation of '{}' carries error code {}: {}",
                    state.flag.key(),
                    state.evaluation.getErrorCode(),
                    state.evaluation.getErrorMessage());
        }
        assertThat(state.evaluation.getValue()).isEqualTo(TckValues.convert(value, state.flag.type()));
    }

    /**
     * Asserts the resolution reason.
     *
     * @param reason the expected reason
     */
    @Then("the reason should be {string}")
    public void theReasonShouldBe(String reason) {
        requireEvaluation();
        assertThat(state.evaluation.getReason()).isEqualTo(reason);
    }

    /**
     * Asserts the resolved variant.
     *
     * @param variant the expected variant
     */
    @Then("the variant should be {string}")
    public void theVariantShouldBe(String variant) {
        requireEvaluation();
        assertThat(state.evaluation.getVariant()).isEqualTo(variant);
    }

    /**
     * Asserts the error code, where an empty string means no error.
     *
     * @param errorCode the expected {@link ErrorCode} name, or an empty string
     */
    @Then("the error-code should be {string}")
    public void theErrorCodeShouldBe(String errorCode) {
        requireEvaluation();
        if (errorCode == null || errorCode.isEmpty()) {
            assertThat(state.evaluation.getErrorCode()).isNull();
        } else {
            assertThat(state.evaluation.getErrorCode()).isEqualTo(ErrorCode.valueOf(errorCode));
        }
    }

    /**
     * Captures the current resolved value so a later evaluation can be asserted to differ.
     */
    @When("the resolved value is remembered")
    public void theResolvedValueIsRemembered() {
        requireEvaluation();
        state.rememberedValue = state.evaluation.getValue();
    }

    /** Asserts that re-evaluation produced a different value than the remembered one. */
    @Then("the resolved details value should have changed")
    public void theResolvedDetailsValueShouldHaveChanged() {
        requireEvaluation();
        assertThat(state.evaluation.getValue())
                .withFailMessage(
                        "Expected '%s' to change after the configuration change, but it is still %s.",
                        state.flag.key(), state.rememberedValue)
                .isNotEqualTo(state.rememberedValue);
    }

    /**
     * Asserts that a resolved structure contains the given entries ({@code key}, {@code type},
     * {@code value} columns).
     *
     * @param expected a table of expected entries
     */
    @Then("the resolved object value should contain")
    public void theResolvedObjectValueShouldContain(DataTable expected) {
        requireEvaluation();
        assertThat(state.evaluation.getValue())
                .as("resolved value of '%s' is a structure", state.flag.key())
                .isInstanceOf(Value.class);
        Structure structure = ((Value) state.evaluation.getValue()).asStructure();
        assertThat(structure)
                .as("resolved value of '%s' is a structure", state.flag.key())
                .isNotNull();

        for (Map<String, String> row : expected.asMaps()) {
            String key = row.get("key");
            Value actual = structure.getValue(key);
            assertThat(actual).as("structure entry '%s'", key).isNotNull();

            Object expectedValue = TckValues.convert(row.get("value"), row.get("type"));
            Object actualValue = actual.asObject();

            // nested numbers compared by value; JSON has one number type
            if (expectedValue instanceof Number && actualValue instanceof Number) {
                assertThat(((Number) actualValue).doubleValue())
                        .as("structure entry '%s'", key)
                        .isEqualTo(((Number) expectedValue).doubleValue());
            } else {
                assertThat(actualValue).as("structure entry '%s'", key).isEqualTo(expectedValue);
            }
        }
    }

    /**
     * Asserts that no error message accompanies the evaluation (requirement 2.3.2).
     */
    @Then("the error message should be empty")
    public void theErrorMessageShouldBeEmpty() {
        requireEvaluation();
        assertThat(state.evaluation.getErrorMessage())
                .as("error message of a successful evaluation of '%s'", state.flag.key())
                .isNullOrEmpty();
    }

    /**
     * Asserts that no recorded call (evaluation, shutdown, initialize) threw.
     */
    @Then("no exception should have been thrown")
    public void noExceptionShouldHaveBeenThrown() {
        assertThat(state.thrown)
                .withFailMessage(
                        "%s threw %s, but the scenario expected it to return normally.", state.thrownBy, state.thrown)
                .isNull();
    }

    /** Asserts the flag under test appears in the payload of the most recently matched event. */
    @Then("the flag should be part of the event payload")
    public void theFlagShouldBePartOfTheEventPayload() {
        ProviderEventRecord event = state.lastEvent.orElseThrow(
                () -> new AssertionError("No event has been matched yet; await an event before asserting its payload"));
        assertThat(event.details().getFlagsChanged()).contains(state.flag.key());
    }

    /**
     * Asserts typed flag-metadata entries on the last evaluation; an entry the table does not list
     * is not checked.
     *
     * <p>The wording and the columns are the specification's own, from
     * {@code assets/gherkin/metadata.feature}: flag metadata is spec behaviour, not a vendor's, so
     * the question belongs here rather than in each adoption's glue.
     *
     * @param table rows with {@code key}, {@code metadata_type} and {@code value} columns
     */
    @Then("the resolved metadata should contain")
    public void theResolvedMetadataShouldContain(DataTable table) {
        ImmutableMetadata metadata = requireMetadata();
        for (Map<String, String> row : table.asMaps(String.class, String.class)) {
            String key = row.get("key");
            String type = row.get("metadata_type");
            Object expected = TckValues.convert(row.get("value"), type);
            Object actual;
            switch (type) {
                case "String":
                    actual = metadata.getString(key);
                    break;
                case "Boolean":
                    actual = metadata.getBoolean(key);
                    break;
                case "Integer":
                    actual = metadata.getInteger(key);
                    break;
                case "Float":
                    actual = metadata.getDouble(key);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown metadata type '" + type
                            + "'. Flag metadata is String, Boolean, Integer or Float.");
            }
            assertThat(actual)
                    .as("metadata '%s' (%s) in %s", key, type, metadata.asUnmodifiableMap())
                    .isEqualTo(expected);
        }
    }

    /** Asserts that the last evaluation carries no flag metadata. */
    @Then("the resolved metadata is empty")
    public void theResolvedMetadataIsEmpty() {
        assertThat(requireMetadata().asUnmodifiableMap())
                .as("flag metadata")
                .isEmpty();
    }

    private ImmutableMetadata requireMetadata() {
        requireEvaluation();
        ImmutableMetadata metadata = state.evaluation.getFlagMetadata();
        assertThat(metadata).as("flag metadata").isNotNull();
        return metadata;
    }

    private void requireEvaluation() {
        if (state.evaluation == null) {
            throw new AssertionError("No evaluation has been performed. "
                    + "Did the scenario forget 'When the flag was evaluated with details'?"
                    + (state.thrown == null ? "" : " " + state.thrownBy + " threw: " + state.thrown));
        }
    }
}
