package dev.openfeature.contrib.tools.providertck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;

/**
 * The vocabulary an adopter declares conformance in, exercised where it is defined.
 *
 * <p>Everything here is something a provider author <em>writes</em> or a reader of a run
 * <em>needs</em>: which capabilities are declarable, which gaps are defects rather than choices,
 * what the configuration under test is called, and which of the two control contracts the run was
 * conducted under. It is all usable with nothing downstream of it — no report, no emitter — which is
 * the point of it living here.
 */
class DeclarationApiTest {

    @Test
    @DisplayName("a reserved capability is not declarable and declaring one fails the run")
    void reservedCapabilitiesAreNotDeclarable() {
        assertThat(Capability.declarable())
                .as("declarable() is every capability some scenario gates")
                .doesNotContain(Capability.TARGETING, Capability.CACHING)
                .contains(Capability.EVENTS, Capability.OBJECT);

        assertThat(Capability.declarableExcept(Capability.STALE))
                .doesNotContain(Capability.STALE, Capability.TARGETING)
                .contains(Capability.EVENTS);

        assertThatThrownBy(() -> Capability.requireDeclarable(EnumSet.allOf(Capability.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TARGETING")
                .hasMessageContaining("declares reserved");
    }

    @Test
    @DisplayName("a capability the Java SDK cannot satisfy is not declarable, and is skipped with that reason")
    void notApplicableCapabilitiesAreNotDeclarable() {
        assertThat(Capability.LARGE_INTEGERS.notApplicable()).isTrue();
        assertThat(Capability.LARGE_INTEGERS.notApplicableReason())
                .as("the reason names the SDK's accessor, which is the limit, rather than the provider")
                .hasValueSatisfying(reason -> assertThat(reason).contains("32-bit"));
        assertThat(Capability.LARGE_INTEGERS.reserved())
                .as("not applicable is distinct from reserved: a scenario does carry the tag")
                .isFalse();

        assertThat(Capability.declarable()).doesNotContain(Capability.LARGE_INTEGERS);
        assertThat(Capability.declarableExcept(Capability.STALE)).doesNotContain(Capability.LARGE_INTEGERS);

        assertThatThrownBy(() -> Capability.requireDeclarable(EnumSet.of(Capability.EVENTS, Capability.LARGE_INTEGERS)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("LARGE_INTEGERS")
                .hasMessageContaining("no Java provider can satisfy");

        // Skipped whatever is declared, and the skip blames the SDK rather than the provider.
        TestAbortedException aborted = catchThrowableOfType(
                () -> CapabilityGate.requireDeclared(Arrays.asList("@large-integers"), Capability.declarable()),
                TestAbortedException.class);
        assertThat(aborted).isNotNull();
        assertThat(aborted)
                .hasMessageContaining("not applicable")
                .hasMessageContaining("32-bit")
                .hasMessageNotContaining("does not declare");
    }

    @Test
    @DisplayName("a tag maps back to the capability it gates")
    void tagsMapBackToCapabilities() {
        assertThat(Capability.fromTag("@numeric-coercion")).contains(Capability.NUMERIC_COERCION);
        assertThat(Capability.fromTag("@not-a-capability")).isEmpty();
    }

    @Test
    @DisplayName("the gate skips an undeclared capability and lets an untagged scenario run")
    void theGateSkipsUndeclaredCapabilities() {
        Set<Capability> declared = EnumSet.of(Capability.EVENTS);

        TestAbortedException aborted = catchThrowableOfType(
                () -> CapabilityGate.requireDeclared(Arrays.asList("@object"), declared), TestAbortedException.class);
        assertThat(aborted)
                .as("an undeclared capability aborts, which is what the JUnit Platform reports as skipped")
                .isNotNull();
        assertThat(aborted).hasMessageContaining("OBJECT").hasMessageContaining("@object");

        CapabilityGate.requireDeclared(Arrays.asList("@events", "@not-a-capability"), declared);
        CapabilityGate.requireDeclared(Collections.emptyList(), declared);
    }

    @Test
    @DisplayName("a deviation records the capability it withholds, tracked or not")
    void deviationsRecordTheCapabilityTheyWithhold() {
        KnownDeviation tracked = KnownDeviation.tracked(
                Capability.NUMERIC_COERCION, "https://example.invalid/1234", "0.5 as an integer returns 0");
        assertThat(tracked.capability).isEqualTo("@numeric-coercion");
        assertThat(tracked.issue).isEqualTo("https://example.invalid/1234");
        assertThat(tracked.summary).isEqualTo("0.5 as an integer returns 0");

        KnownDeviation untracked = KnownDeviation.untracked(null, "a gap against a mandatory scenario");
        assertThat(untracked.capability).isNull();
        assertThat(untracked.issue).isNull();
    }

    @Test
    @DisplayName("a configuration name is derived from the suite class, and is overridable")
    void configurationNamesAreDerivedFromTheSuiteClass() {
        assertThat(new TckSuiteFixture().configuration()).isEqualTo("tck-suite-fixture");
        assertThat(new NamedConfiguration().configuration()).isEqualTo("a-name-of-my-own");
    }

    @Test
    @DisplayName("an adopter declares nothing by default, which is silence rather than a claim")
    void theDefaultsAreSilence() {
        assertThat(new TckSuiteFixture().knownDeviations()).isEmpty();
        assertThat(new TckSuiteFixture().capabilities()).isEqualTo(Capability.declarable());
    }

    @Test
    @DisplayName("a backend says which of the two control contracts drove it")
    void backendsSayHowTheyWereDriven() {
        assertThat(new InProcessBackendControl().controlApi())
                .as("a provider with no backend is controlled in-process, the narrow allowance")
                .isEqualTo(BackendControl.CONTROL_API_IN_PROCESS);

        assertThat(BackendControl.CONTROL_API_HTTP).isEqualTo("http");
        assertThat(BackendControl.CONTROL_API_IN_PROCESS).isEqualTo("in-process");
    }

    /** A suite that names its configuration rather than taking the derived name. */
    private static final class NamedConfiguration extends TckSuiteFixture {
        @Override
        public String configuration() {
            return "a-name-of-my-own";
        }
    }
}
