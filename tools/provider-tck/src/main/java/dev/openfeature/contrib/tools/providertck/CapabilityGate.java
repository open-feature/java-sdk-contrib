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
     * <p>One skip, carrying its reason, is the whole mechanism. A capability that cannot hold in a
     * language at all — {@link Capability#LARGE_INTEGERS} on the Java SDK's 32-bit integer accessor
     * — is undeclared like any other the provider does not offer, and is skipped the same way.
     * Separating the two would ask a reader to learn a second vocabulary to be told what the
     * declaration and the scenario's own tags already say; where the impossibility is the
     * language's, Appendix F records it once instead.
     *
     * @param tags the scenario's Gherkin tags, including the leading at-sign
     * @param declared the capabilities the provider declares
     * @throws TestAbortedException if a tag gates an undeclared capability
     */
    public static void requireDeclared(Collection<String> tags, Set<Capability> declared) {
        for (String tag : tags) {
            Optional<Capability> capability = Capability.fromTag(tag);
            if (!capability.isPresent()) {
                continue;
            }
            if (!declared.contains(capability.get())) {
                throw new TestAbortedException("Skipped: provider does not declare capability "
                        + capability.get().name() + " (tag " + tag + "). Declared capabilities: " + declared);
            }
        }
    }
}
