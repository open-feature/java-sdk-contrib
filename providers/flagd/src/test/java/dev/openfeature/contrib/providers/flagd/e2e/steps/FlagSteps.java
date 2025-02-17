package dev.openfeature.contrib.providers.flagd.e2e.steps;

import static org.assertj.core.api.Assertions.assertThat;

import dev.openfeature.contrib.providers.flagd.e2e.State;
import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.Value;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.junit.jupiter.api.parallel.Isolated;

@Isolated()
public class FlagSteps extends AbstractSteps {

    public FlagSteps(State state) {
        super(state);
    }

    @Given("a {}-flag with key {string} and a default value {string}")
    public void givenAFlag(String type, String name, String defaultValue) throws Throwable {
        state.flag = new Flag(type, name, Utils.convert(defaultValue, type));
    }

    @When("the flag was evaluated with details")
    public void the_flag_was_evaluated_with_details() throws InterruptedException {
        FlagEvaluationDetails details;
        switch (state.flag.type) {
            case "String":
                details =
                        state.client.getStringDetails(state.flag.name, (String) state.flag.defaultValue, state.context);
                break;
            case "Boolean":
                details = state.client.getBooleanDetails(
                        state.flag.name, (Boolean) state.flag.defaultValue, state.context);
                break;
            case "Float":
                details =
                        state.client.getDoubleDetails(state.flag.name, (Double) state.flag.defaultValue, state.context);
                break;
            case "Integer":
                details = state.client.getIntegerDetails(
                        state.flag.name, (Integer) state.flag.defaultValue, state.context);
                break;
            case "Object":
                details =
                        state.client.getObjectDetails(state.flag.name, (Value) state.flag.defaultValue, state.context);
                break;
            default:
                throw new AssertionError();
        }
        state.evaluation = details;
    }

    @Then("the resolved details value should be \"{}\"")
    public void the_resolved_details_value_should_be(String value) throws Throwable {
        assertThat(state.evaluation.getValue()).isEqualTo(Utils.convert(value, state.flag.type));
    }

    @Then("the reason should be {string}")
    public void the_reason_should_be(String reason) {
        assertThat(state.evaluation.getReason()).isEqualTo(reason);
    }

    @Then("the variant should be {string}")
    public void the_variant_should_be(String variant) {
        assertThat(state.evaluation.getVariant()).isEqualTo(variant);
    }

    @Then("the flag should be part of the event payload")
    public void the_flag_was_modified() {
        Event event = state.lastEvent.orElseThrow(AssertionError::new);
        assertThat(event.details.getFlagsChanged()).contains(state.flag.name);
    }

    public class Flag {
        String name;
        Object defaultValue;
        String type;

        public Flag(String type, String name, Object defaultValue) {
            this.name = name;
            this.defaultValue = defaultValue;
            this.type = type;
        }
    }
}
