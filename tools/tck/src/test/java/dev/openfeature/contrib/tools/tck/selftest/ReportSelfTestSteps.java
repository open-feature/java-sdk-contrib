package dev.openfeature.contrib.tools.tck.selftest;

import dev.openfeature.contrib.tools.tck.Capability;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.cucumber.java.en.Given;
import java.util.EnumSet;
import java.util.Set;

/**
 * Glue for {@code report-selftest/report.feature}. It stands in for a provider with no backend, but
 * gates on {@link Capability#requireDeclared} exactly as {@code ProviderSteps.gateOnCapabilities}
 * does, so an abort here becomes a skip by the same route.
 */
public class ReportSelfTestSteps {

    private static volatile Set<Capability> declared = Capability.declarable();

    /**
     * Sets the capabilities the fixture provider declares, for the run that is about to start.
     *
     * @param capabilities the declared capabilities
     */
    public static void declare(Set<Capability> capabilities) {
        declared = EnumSet.copyOf(capabilities);
    }

    /**
     * Gates the scenario on the declared capabilities, exactly as the real suite does.
     *
     * @param scenario the scenario about to run
     */
    @Before(order = 0)
    public void gateOnCapabilities(Scenario scenario) {
        Capability.requireDeclared(scenario.getUri(), scenario.getSourceTagNames(), declared);
    }

    /** A step that does nothing, successfully. */
    @Given("a step that passes")
    public void aStepThatPasses() {
        // A passing scenario needs a step that passes and nothing more.
    }

    /** A step that fails with an {@link AssertionError}, as a failing assertion in a step definition does. */
    @Given("a step that fails")
    public void aStepThatFails() {
        throw new AssertionError("this fixture scenario is expected to fail");
    }

    /**
     * A passing step taking its arguments from an Examples row. The parameters are unused; they make
     * the outline's rows differ in compiled step text as well as in table row.
     *
     * @param key the flag key from the row
     * @param requested the requested type from the row
     */
    @Given("a step that passes with {string} as {string}")
    public void aStepThatPassesWith(String key, String requested) {
        // Interpolated into the pickle's step text by Cucumber; nothing to do here.
    }
}
