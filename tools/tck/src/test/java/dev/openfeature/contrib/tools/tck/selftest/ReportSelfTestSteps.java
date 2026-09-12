package dev.openfeature.contrib.tools.tck.selftest;

import dev.openfeature.contrib.tools.tck.Capability;
import dev.openfeature.contrib.tools.tck.CapabilityGate;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.cucumber.java.en.Given;
import java.util.EnumSet;
import java.util.Set;

/**
 * Glue for {@code report-selftest/report.feature}, the fixture the conformance report tests run.
 *
 * <p>It stands in for a provider, and for nothing else: there is no Compose stack, no control API
 * and no provider here, because none of that is what the report tests are about. What it does share
 * with the real suite is the part that matters — the capability gate is
 * {@link CapabilityGate#requireDeclared}, the same call the real
 * {@code ProviderSteps.gateOnCapabilities} makes from the same kind of {@code @Before(order = 0)}
 * hook. A fixture that aborted by some other route would prove only that some abort becomes a skip.
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
        CapabilityGate.requireDeclared(scenario.getSourceTagNames(), declared);
    }

    /** A step that does nothing, successfully. */
    @Given("a step that passes")
    public void aStepThatPasses() {
        // A passing scenario needs a step that passes and nothing more.
    }

    /**
     * A step that does nothing, unsuccessfully.
     *
     * <p>An {@link AssertionError} rather than a checked failure, because that is what a failing
     * assertion in a step definition throws, and the point is what the results stream makes of it.
     */
    @Given("a step that fails")
    public void aStepThatFails() {
        throw new AssertionError("this fixture scenario is expected to fail");
    }

    /**
     * A passing step that takes its arguments from an Examples row.
     *
     * <p>The parameters are unused. They exist so the outline's rows differ in their compiled step
     * text as well as in their table row, which is how a consumer that reads only the steps can
     * still tell the rows apart.
     *
     * @param key the flag key from the row
     * @param requested the requested type from the row
     */
    @Given("a step that passes with {string} as {string}")
    public void aStepThatPassesWith(String key, String requested) {
        // Interpolated into the pickle's step text by Cucumber; nothing to do here.
    }
}
