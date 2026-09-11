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
                .doesNotContain(Capability.CACHING)
                .contains(Capability.EVENTS, Capability.OBJECT);

        assertThat(Capability.declarableExcept(Capability.STALE))
                .doesNotContain(Capability.STALE, Capability.CACHING)
                .contains(Capability.EVENTS);

        assertThatThrownBy(() -> Capability.requireDeclarable(EnumSet.allOf(Capability.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CACHING")
                .hasMessageContaining("declares reserved");
    }

    @Test
    @DisplayName("targeting is a declarable capability, not a reserved one")
    void targetingIsDeclarable() {
        // It was reserved while no scenario carried the tag. targeting-key-flag's three scenarios
        // carry it now, so it gates something and the claim can be contradicted by a result --
        // which is the whole test for whether a capability may be declared.
        assertThat(Capability.fromTag("@targeting")).contains(Capability.TARGETING);
        assertThat(Capability.TARGETING.reserved()).isFalse();
        assertThat(Capability.declarable()).contains(Capability.TARGETING);
        Capability.requireDeclarable(EnumSet.of(Capability.TARGETING));

        // And the "declare everything" shortcut still excludes @caching, which is now the only
        // reserved tag. That is the accident the shortcut exists to prevent, not a general one.
        assertThat(Capability.CACHING.reserved()).isTrue();
        assertThat(Capability.declarable()).doesNotContain(Capability.CACHING);
        assertThat(Capability.declarableExcept(Capability.TARGETING))
                .doesNotContain(Capability.TARGETING, Capability.CACHING);
    }

    @Test
    @DisplayName("variants is declarable, because 2.2.4 is a SHOULD and the field is optional")
    void variantsIsADeclarableChoice() {
        // Requirement 2.2.4 says a provider SHOULD populate the variant, and types.md types the
        // field optional, so a backend with no variant concept withholds the tag rather than
        // recording a deviation against a MUST that does not exist.
        assertThat(Capability.fromTag("@variants")).contains(Capability.VARIANTS);
        assertThat(Capability.VARIANTS.reserved()).isFalse();
        assertThat(Capability.declarable()).contains(Capability.VARIANTS);

        TestAbortedException aborted = catchThrowableOfType(
                () -> CapabilityGate.requireDeclared(
                        Arrays.asList("@variants"), Capability.declarableExcept(Capability.VARIANTS)),
                TestAbortedException.class);
        assertThat(aborted).isNotNull();
        assertThat(aborted)
                .hasMessageContaining("VARIANTS")
                .hasMessageContaining("@variants")
                .hasMessageContaining("does not declare");
    }

    @Test
    @DisplayName("a capability a language cannot hold is an ordinary one, withheld and skipped like any other")
    void aCapabilityTheLanguageCannotHoldIsWithheldRatherThanSetApart() {
        // @large-integers asks for 2^53 - 1 and the Java SDK's accessor is a 32-bit Integer, so no
        // Java provider can hold it. That is a property of the SDK, recorded once in Appendix F,
        // and not a second kind of declaration: there is one skip and it carries its reason.
        assertThat(Capability.LARGE_INTEGERS.reserved())
                .as("a scenario does carry the tag, so there is something to gate")
                .isFalse();
        assertThat(Capability.declarable())
                .as("it is an ordinary declarable capability; a harness withholds it rather than being forbidden it")
                .contains(Capability.LARGE_INTEGERS);
        assertThat(Capability.declarableExcept(Capability.LARGE_INTEGERS))
                .as("declarableExcept is how a Java harness says so")
                .doesNotContain(Capability.LARGE_INTEGERS);

        // Declaring it is not refused. The guard exists for a claim no result can contradict, and
        // this is not one: the scenario runs and fails, which says more than a rejected declaration.
        Capability.requireDeclarable(EnumSet.of(Capability.EVENTS, Capability.LARGE_INTEGERS));

        // Withheld, it is skipped exactly as any undeclared capability is.
        TestAbortedException aborted = catchThrowableOfType(
                () -> CapabilityGate.requireDeclared(
                        Arrays.asList("@large-integers"), Capability.declarableExcept(Capability.LARGE_INTEGERS)),
                TestAbortedException.class);
        assertThat(aborted).isNotNull();
        assertThat(aborted)
                .hasMessageContaining("LARGE_INTEGERS")
                .hasMessageContaining("@large-integers")
                .hasMessageContaining("does not declare");
    }

    @Test
    @DisplayName("a tag maps back to the capability it gates")
    void tagsMapBackToCapabilities() {
        assertThat(Capability.fromTag("@numeric-coercion")).contains(Capability.NUMERIC_COERCION);
        assertThat(Capability.fromTag("@not-a-capability")).isEmpty();
    }

    @Test
    @DisplayName("reinitialisation is a declarable choice, and withholding it skips only its own scenario")
    void reinitialisationIsADeclarableChoice() {
        // Requirement 2.5.2 permits reuse after shutdown rather than requiring it, so a provider
        // that refuses it withholds the tag instead of recording a deviation. That makes it an
        // ordinary declarable capability rather than a reserved one.
        assertThat(Capability.fromTag("@reinitialization")).contains(Capability.REINITIALIZATION);
        assertThat(Capability.REINITIALIZATION.reserved()).isFalse();
        assertThat(Capability.declarable()).contains(Capability.REINITIALIZATION);

        // The scenario carries @lifecycle too. A provider that initialises against a backend it
        // does not reopen declares the first and withholds the second, and only the one scenario
        // is skipped -- the rest of the lifecycle set still runs.
        Set<Capability> reachesBackendButNoReuse = EnumSet.of(Capability.LIFECYCLE);
        CapabilityGate.requireDeclared(Arrays.asList("@lifecycle"), reachesBackendButNoReuse);

        TestAbortedException aborted = catchThrowableOfType(
                () -> CapabilityGate.requireDeclared(
                        Arrays.asList("@lifecycle", "@reinitialization"), reachesBackendButNoReuse),
                TestAbortedException.class);
        assertThat(aborted).isNotNull();
        assertThat(aborted)
                .hasMessageContaining("REINITIALIZATION")
                .hasMessageContaining("@reinitialization")
                .hasMessageContaining("does not declare");
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
