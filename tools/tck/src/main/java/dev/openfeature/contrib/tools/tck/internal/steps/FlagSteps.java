package dev.openfeature.contrib.tools.tck.internal.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.withinPercentage;

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

    // float carries about seven significant digits, so this covers its widening and nothing else
    private static final double FLOAT_TOLERANCE_PERCENT = 0.001;

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
        Map<String, Object> metadata = requireMetadata().asUnmodifiableMap();
        for (Map<String, String> row : table.asMaps(String.class, String.class)) {
            String key = row.get("key");
            String type = row.get("metadata_type");
            // Asserted before the value, because a row whose value is "null" would otherwise pass
            // against a provider that omitted the entry: the conversion yields null, and so does a
            // lookup of a key nobody set.
            assertThat(metadata).as("flag metadata").containsKey(key);
            assertMetadataValue(key, type, row.get("value"), metadata);
        }
    }

    /**
     * Compares one metadata entry by its declared type rather than by the Java type it arrived in.
     *
     * <p>{@link ImmutableMetadata}'s getters are exact: {@code getDouble} answers null for a value
     * stored through {@code addFloat}, and {@code getInteger} null for one stored as a long. Reading
     * through them would fail a provider whose value is right and whose box is not, and a scenario
     * asks about the value.
     *
     * <p>{@code Float} is compared with a tolerance for the same reason -- a 32-bit float widened to
     * a double is not the double the feature file's decimal parses to: {@code 0.1f} widens to
     * 0.10000000149011612 while the table says 0.1.
     *
     * @param key the metadata key the row names
     * @param type the declared metadata type
     * @param value the expected value, as the feature file writes it
     * @param metadata every entry the provider sent, for the failure message
     */
    private void assertMetadataValue(String key, String type, String value, Map<String, Object> metadata) {
        Object actual = metadata.get(key);
        String where = String.format("metadata '%s' (%s) in %s", key, type, metadata);
        switch (type) {
            case "String":
            case "Boolean":
                assertThat(actual).as(where).isEqualTo(TckValues.convert(value, type));
                break;
            case "Integer":
                // parsed as a long, because metadata is not read back through getIntegerDetails and
                // a provider may carry a number wider than 32 bits
                assertThat(numberOf(actual, where).longValue()).as(where).isEqualTo(Long.parseLong(value));
                break;
            case "Float":
                assertThat(numberOf(actual, where).doubleValue())
                        .as(where)
                        .isCloseTo(Double.parseDouble(value), withinPercentage(FLOAT_TOLERANCE_PERCENT));
                break;
            default:
                throw new IllegalArgumentException(
                        "Unknown metadata type '" + type + "'. Flag metadata is String, Boolean, Integer or Float.");
        }
    }

    private static Number numberOf(Object actual, String where) {
        assertThat(actual).as("%s, which this row declares numeric", where).isInstanceOf(Number.class);
        return (Number) actual;
    }

    /** Asserts that the last evaluation carries no flag metadata. */
    @Then("the resolved metadata is empty")
    public void theResolvedMetadataIsEmpty() {
        assertThat(requireMetadata().asUnmodifiableMap()).as("flag metadata").isEmpty();
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
