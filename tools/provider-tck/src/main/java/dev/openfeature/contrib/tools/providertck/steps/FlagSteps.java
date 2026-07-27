package dev.openfeature.contrib.tools.providertck.steps;

import static org.assertj.core.api.Assertions.assertThat;

import dev.openfeature.contrib.tools.providertck.FlagUnderTest;
import dev.openfeature.contrib.tools.providertck.ProviderEventRecord;
import dev.openfeature.contrib.tools.providertck.TckState;
import dev.openfeature.contrib.tools.providertck.TckValues;
import dev.openfeature.sdk.ErrorCode;
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
 * Flag evaluation steps and assertions on the resulting resolution details.
 *
 * <p>Step vocabulary is inherited verbatim from the flagd test harness, which was already
 * provider-neutral here.
 */
public class FlagSteps extends AbstractSteps {

    private static final Logger log = LoggerFactory.getLogger(FlagSteps.class);

    public FlagSteps(TckState state) {
        super(state);
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
     * Evaluates the declared flag through the typed API matching its declared type.
     *
     * <p>Dispatch is on the declared type alone, which is what makes the integer/float distinction
     * observable: an {@code Integer} flag goes through {@code getIntegerDetails} and a {@code Float}
     * flag through {@code getDoubleDetails}, with no widening in between. A provider that returns a
     * double for an integer flag fails here rather than being quietly accommodated.
     *
     * <p>Exceptions are recorded rather than propagated. The SDK contract is that typed evaluation
     * never throws — errors surface as an error code plus the code default — so
     * {@code no exception should have been thrown} can assert that explicitly instead of the
     * scenario merely erroring out.
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
            state.evaluationException = e;
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
     *
     * <p>Added by the TCK, for the configuration-change scenario. The control API only requires
     * that {@code POST /change} changes the resolved value of {@code changing-flag}; which concrete
     * value it changes to is vendor-defined. Asserting a delta rather than an absolute keeps the
     * scenario portable and independent of how many times it has run against the same stack.
     */
    @When("the resolved value is remembered")
    public void theResolvedValueIsRemembered() {
        requireEvaluation();
        state.rememberedValue = state.evaluation.getValue();
    }

    /**
     * Asserts that re-evaluation produced a different value than the remembered one.
     */
    @Then("the resolved details value should have changed")
    public void theResolvedDetailsValueShouldHaveChanged() {
        requireEvaluation();
        assertThat(state.evaluation.getValue())
                .withFailMessage(
                        "Expected the value of '%s' to differ after the configuration change, "
                                + "but it is still %s. The provider signalled the change but did not apply it.",
                        state.flag.key(), state.rememberedValue)
                .isNotEqualTo(state.rememberedValue);
    }

    /**
     * Asserts that a resolved structure contains the given entries.
     *
     * <p>Table columns are {@code key}, {@code type} and {@code value}, mirroring the shape of the
     * flagd harness's metadata table. Asserting individual entries rather than a whole JSON blob
     * keeps the step readable and avoids quoting a JSON document inside a Gherkin cell.
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

            // Numbers nested inside a structure are compared by value rather than by Java type.
            // Structures arrive as JSON, and JSON has a single number type — whether 100 comes
            // back as an Integer or a Double is an artefact of the provider's JSON library, not
            // an observable part of the provider contract. The integer/float distinction that
            // *is* part of the contract applies to top-level typed evaluation, and is asserted
            // by the dedicated scenarios in evaluation.feature and errors.feature.
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
     * Asserts that the evaluation returned normally.
     *
     * <p>Added by the TCK. The spec requires typed evaluation to absorb every error into the
     * returned details, so an error scenario must prove both halves: the right error code, and no
     * exception escaping to the caller.
     */
    @Then("no exception should have been thrown")
    public void noExceptionShouldHaveBeenThrown() {
        assertThat(state.evaluationException)
                .withFailMessage(
                        "Evaluation threw %s, but typed evaluation must never throw — "
                                + "errors belong in the resolution details.",
                        state.evaluationException)
                .isNull();
    }

    /**
     * Asserts the flag under test appears in the payload of the most recently matched event.
     */
    @Then("the flag should be part of the event payload")
    public void theFlagShouldBePartOfTheEventPayload() {
        ProviderEventRecord event = state.lastEvent.orElseThrow(
                () -> new AssertionError("No event has been matched yet; await an event before asserting its payload"));
        assertThat(event.details().getFlagsChanged()).contains(state.flag.key());
    }

    private void requireEvaluation() {
        if (state.evaluation == null) {
            throw new AssertionError("No evaluation has been performed. "
                    + "Did the scenario forget 'When the flag was evaluated with details'?"
                    + (state.evaluationException == null ? "" : " Evaluation threw: " + state.evaluationException));
        }
    }
}
