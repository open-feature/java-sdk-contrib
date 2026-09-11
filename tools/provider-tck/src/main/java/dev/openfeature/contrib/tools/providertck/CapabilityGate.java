package dev.openfeature.contrib.tools.providertck;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import org.opentest4j.TestAbortedException;

/**
 * Skips a scenario that needs a capability the provider did not declare.
 *
 * <p>One implementation, deliberately. This is the rule the whole suite rests on — a scenario
 * skipped for an undeclared capability must be reported as skipped and never as passed — so the
 * gate that produces the skip and the tests that prove the skip survives into the results have to
 * be looking at the same code. Inlined into the step definitions, a self-test could only
 * demonstrate that some abort becomes a skip, not that <em>this</em> abort does.
 *
 * <p>Aborting rather than failing is what makes the outcome a skip: {@link TestAbortedException} maps
 * to {@code SKIPPED} in Cucumber's step results, which is what reaches the results.
 */
public final class CapabilityGate {

    private CapabilityGate() {}

    /**
     * Aborts the running scenario if any of its tags gates a capability that was not declared.
     *
     * <p>Tags that gate nothing are ignored, so a scenario with no capability tag is mandatory and
     * always runs.
     *
     * @param tags the scenario's Gherkin tags, including the leading at-sign
     * @param declared the capabilities the provider declares
     * @throws TestAbortedException if a tag gates an undeclared capability
     */
    public static void requireDeclared(Collection<String> tags, Set<Capability> declared) {
        for (String tag : tags) {
            Optional<Capability> capability = Capability.fromTag(tag);
            if (capability.isPresent() && !declared.contains(capability.get())) {
                throw new TestAbortedException("Skipped: provider does not declare capability "
                        + capability.get().name() + " (tag " + tag + "). Declared capabilities: " + declared);
            }
        }
    }
}
