package dev.openfeature.contrib.tools.tck;

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
    @DisplayName("a capability the Java SDK cannot express is refused here, not left to every adopter")
    void aCapabilityTheSdkCannotExpressIsRefusedCentrally() {
        // @large-integers asks for 2^53 - 1 and the Java SDK's accessor is a 32-bit Integer, so no
        // Java provider can be asked it -- ever, until the SDK changes. Three suites in this
        // repository used to withhold it by hand, each with its own comment restating this
        // paragraph. The implementation refuses it instead, so an adopter neither has to know it
        // nor can get it wrong.
        assertThat(Capability.LARGE_INTEGERS.inexpressible()).isTrue();
        assertThat(Capability.declarable())
                .as("no Java provider may claim it, so \"everything\" does not include it")
                .doesNotContain(Capability.LARGE_INTEGERS);
        assertThat(Capability.declarableExcept(Capability.EVENTS))
                .as("nor does \"everything except\", which is what the adoptions call")
                .doesNotContain(Capability.LARGE_INTEGERS);

        assertThatThrownBy(() -> Capability.requireDeclarable(EnumSet.of(Capability.EVENTS, Capability.LARGE_INTEGERS)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("LARGE_INTEGERS")
                .hasMessageContaining("@large-integers")
                .as("the message says why, naming the SDK property rather than citing a rule")
                .hasMessageContaining("Client.getIntegerDetails")
                .hasMessageContaining("cannot express");
    }

    @Test
    @DisplayName("the two refusals are different facts, and neither message could be mistaken for the other")
    void reservedAndInexpressibleAreToldApart() {
        // A reader who sees a capability missing from a report has to be able to tell "no scenario
        // anywhere carries this tag yet" from "the scenarios exist and this SDK cannot ask them".
        // Only the second is permanent, and neither says anything about the provider under test --
        // which is the third thing they must not be mistaken for.
        assertThat(Capability.CACHING.reserved()).isTrue();
        assertThat(Capability.CACHING.inexpressible())
                .as("a reservation is global and expires; it is not a language's limit")
                .isFalse();
        assertThat(Capability.LARGE_INTEGERS.reserved())
                .as("scenarios do carry @large-integers, which is what makes it not a reservation")
                .isFalse();

        String reserved = catchThrowableOfType(
                        () -> Capability.requireDeclarable(EnumSet.of(Capability.CACHING)),
                        IllegalArgumentException.class)
                .getMessage();
        String inexpressible = catchThrowableOfType(
                        () -> Capability.requireDeclarable(EnumSet.of(Capability.LARGE_INTEGERS)),
                        IllegalArgumentException.class)
                .getMessage();

        assertThat(reserved)
                .as("the reserved refusal says there is nothing to gate yet")
                .contains("no scenario in the suite carries");
        assertThat(inexpressible)
                .as("the inexpressible refusal says the opposite: the scenarios exist elsewhere")
                .contains("the scenarios exist and are asked in languages whose API is wide enough")
                .contains("This is not a reserved capability")
                .doesNotContain("no scenario in the suite carries");

        // And the same distinction survives into the skip, which is where a report's reader meets
        // it. An undeclared capability names the provider; an inexpressible one must not, because
        // the provider had no say.
        TestAbortedException undeclared = catchThrowableOfType(
                () -> CapabilityGate.requireDeclared(
                        Arrays.asList("@variants"), Capability.declarableExcept(Capability.VARIANTS)),
                TestAbortedException.class);
        TestAbortedException unaskable = catchThrowableOfType(
                () -> CapabilityGate.requireDeclared(Arrays.asList("@large-integers"), Capability.declarable()),
                TestAbortedException.class);

        assertThat(undeclared).hasMessageContaining("provider does not declare capability");
        assertThat(unaskable)
                .hasMessageContaining("the Java SDK cannot express capability LARGE_INTEGERS")
                .hasMessageContaining("Client.getIntegerDetails")
                .as("not the provider's decision, and the reason has to say so")
                .hasMessageContaining("not the provider under test declining");
        assertThat(unaskable.getMessage()).doesNotContain("provider does not declare");

        // The inexpressible reason is reached whatever the declaration says, because a declaration
        // cannot contain it. Checking it after the declaration would make the right reason appear
        // only by luck.
        TestAbortedException evenIfSomehowDeclared = catchThrowableOfType(
                () -> CapabilityGate.requireDeclared(
                        Arrays.asList("@large-integers"), EnumSet.of(Capability.LARGE_INTEGERS)),
                TestAbortedException.class);
        assertThat(evenIfSomehowDeclared).hasMessageContaining("the Java SDK cannot express capability");
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
    @DisplayName("a deviation records the capability the gap is about, tracked or not")
    void deviationsRecordTheCapabilityTheGapIsAbout() {
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
    @DisplayName("a backend says which of the two control contracts drove it, and cannot stay silent")
    void backendsSayHowTheyWereDriven() throws NoSuchMethodException {
        assertThat(new InProcessBackendControl().controlApi())
                .as("a provider with no backend is controlled in-process, the narrow allowance")
                .isEqualTo(ControlApi.IN_PROCESS);

        assertThat(ControlApi.HTTP.wireValue()).isEqualTo("http");
        assertThat(ControlApi.IN_PROCESS.wireValue()).isEqualTo("in-process");

        // Closed on purpose: the report schema's enum has exactly these two members, and there is
        // no third case an unanswered value would legitimately cover.
        assertThat(ControlApi.values()).containsExactly(ControlApi.HTTP, ControlApi.IN_PROCESS);

        // Required on purpose: a default here would answer a question only the author of a custom
        // control can answer, so the compiler has to ask for it.
        assertThat(BackendControl.class.getMethod("controlApi").isDefault())
                .as("controlApi() must be abstract, so that a custom control states it")
                .isFalse();
    }

    /** A suite that names its configuration rather than taking the derived name. */
    private static final class NamedConfiguration extends TckSuiteFixture {
        @Override
        public String configuration() {
            return "a-name-of-my-own";
        }
    }
}
