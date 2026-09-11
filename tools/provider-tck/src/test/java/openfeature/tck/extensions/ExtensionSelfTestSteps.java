package openfeature.tck.extensions;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;

/**
 * Step definitions for the TCK's own extension fixture, in the package an adopter would use.
 *
 * <p>Test-scoped, so it is not in the released JAR. It exists to demonstrate the claim the extension
 * point makes: a step class in {@code openfeature.tck.extensions} is on the suite's glue path
 * without an annotation, a runner or a line of configuration written for it.
 *
 * <p>The steps deliberately need nothing from {@link
 * dev.openfeature.contrib.tools.providertck.TckRuntime}. An extension scenario in a real suite runs
 * after the canonical {@code @BeforeAll} and has the started backend and its control — but asserting
 * that here would make the TCK's own unit tests need Docker, which is a worse trade than proving the
 * lifecycle structurally: the extension features are discovered into the same suite and the same
 * Cucumber engine descriptor as the canonical ones, which is what a shared {@code @BeforeAll}
 * <em>is</em>.
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
