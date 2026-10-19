package openfeature.tck.extensions;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;

/**
 * Steps for the TCK's own extension fixture, proving {@code openfeature.tck.extensions} is on the
 * glue path with no configuration.
 */
public class ExtensionSelfTestSteps {

    private boolean ran;

    /** A step Cucumber can only find if the extension glue package is on the glue path. */
    @Given("a step defined in the extension glue package")
    public void aStepDefinedInTheExtensionGluePackage() {
        ran = true;
    }

    /** Asserts the step above ran, so that a missing glue package fails rather than passes. */
    @Then("the extension step ran")
    public void theExtensionStepRan() {
        assertThat(ran)
                .as("the extension glue package was resolved and its steps executed")
                .isTrue();
    }
}
