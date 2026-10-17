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

/** Tests the declaration vocabulary: capabilities, deviations, configuration names and control APIs. */
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
        // targeting-key-flag scenarios carry the tag, so it gates something
        assertThat(Capability.fromTag("@targeting")).contains(Capability.TARGETING);
        assertThat(Capability.TARGETING.reserved()).isFalse();
        assertThat(Capability.declarable()).contains(Capability.TARGETING);
        Capability.requireDeclarable(EnumSet.of(Capability.TARGETING));

        // "declare everything" still excludes @caching, the only reserved tag
        assertThat(Capability.CACHING.reserved()).isTrue();
        assertThat(Capability.declarable()).doesNotContain(Capability.CACHING);
        assertThat(Capability.declarableExcept(Capability.TARGETING))
                .doesNotContain(Capability.TARGETING, Capability.CACHING);
    }

    @Test
    @DisplayName("variants is declarable, because 2.2.4 is a SHOULD and the field is optional")
    void variantsIsADeclarableChoice() {
        // 2.2.4 is a SHOULD, so a backend without variants withholds the tag
        assertThat(Capability.fromTag("@variants")).contains(Capability.VARIANTS);
        assertThat(Capability.VARIANTS.reserved()).isFalse();
        assertThat(Capability.declarable()).contains(Capability.VARIANTS);

        TestAbortedException aborted = catchThrowableOfType(
                () -> Capability.requireDeclared(
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
        // 2^53 - 1 does not fit the SDK's 32-bit Integer accessor
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
                .as("the message names the SDK limitation")
                .hasMessageContaining("Client.getIntegerDetails")
                .hasMessageContaining("cannot express");
    }

    @Test
    @DisplayName("the two refusals are different facts, and neither message could be mistaken for the other")
    void reservedAndInexpressibleAreToldApart() {
        // "no scenario carries this yet" vs "this SDK cannot ask it"; neither blames the provider
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
                .contains("no scenario in the suite carries")
                .as("and never blames the SDK, because a reservation is every language's")
                .doesNotContain("SDK");
        assertThat(inexpressible)
                .as("the inexpressible refusal says the opposite: the scenarios exist elsewhere")
                .contains("the scenarios exist and are asked in languages whose API is wide enough")
                .contains("This is not a reserved capability")
                .doesNotContain("no scenario in the suite carries");

        // same distinction in the skip message
        TestAbortedException undeclared = catchThrowableOfType(
                () -> Capability.requireDeclared(
                        Arrays.asList("@variants"), Capability.declarableExcept(Capability.VARIANTS)),
                TestAbortedException.class);
        TestAbortedException unaskable = catchThrowableOfType(
                () -> Capability.requireDeclared(Arrays.asList("@large-integers"), Capability.declarable()),
                TestAbortedException.class);

        assertThat(undeclared).hasMessageContaining("provider does not declare capability");
        assertThat(unaskable)
                .hasMessageContaining("the Java SDK cannot express capability LARGE_INTEGERS")
                .hasMessageContaining("Client.getIntegerDetails")
                .as("not the provider's decision, and the reason has to say so")
                .hasMessageContaining("not the provider under test declining");
        assertThat(unaskable.getMessage()).doesNotContain("provider does not declare");

        // inexpressible reason wins regardless of declaration
        TestAbortedException evenIfSomehowDeclared = catchThrowableOfType(
                () -> Capability.requireDeclared(
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
        // 2.5.2 permits but does not require reuse after shutdown
        assertThat(Capability.fromTag("@reinitialization")).contains(Capability.REINITIALIZATION);
        assertThat(Capability.REINITIALIZATION.reserved()).isFalse();
        assertThat(Capability.declarable()).contains(Capability.REINITIALIZATION);

        // withholding it skips only the @reinitialization scenario, not all of @lifecycle
        Set<Capability> reachesBackendButNoReuse = EnumSet.of(Capability.LIFECYCLE);
        Capability.requireDeclared(Arrays.asList("@lifecycle"), reachesBackendButNoReuse);

        TestAbortedException aborted = catchThrowableOfType(
                () -> Capability.requireDeclared(
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
                () -> Capability.requireDeclared(Arrays.asList("@object"), declared), TestAbortedException.class);
        assertThat(aborted)
                .as("an undeclared capability aborts, which is what the JUnit Platform reports as skipped")
                .isNotNull();
        assertThat(aborted).hasMessageContaining("OBJECT").hasMessageContaining("@object");

        Capability.requireDeclared(Arrays.asList("@events", "@not-a-capability"), declared);
        Capability.requireDeclared(Collections.emptyList(), declared);
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
    @DisplayName("a deviation must say what the gap is")
    void deviationsMustCarryASummary() {
        // a deviation without a summary cannot be acted on
        assertThatThrownBy(() -> KnownDeviation.untracked(Capability.OBJECT, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no summary");

        assertThatThrownBy(() -> KnownDeviation.tracked(Capability.OBJECT, "https://example.invalid/1", "   "))
                .as("blank is the same omission with whitespace in it")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no summary");
    }

    @Test
    @DisplayName("a deviation cannot name a capability whose scenarios were never put to the provider")
    void deviationsCannotNameAnUnaskedCapability() {
        // reserved and inexpressible capabilities are never asked, so cannot deviate
        assertThatThrownBy(() -> KnownDeviation.untracked(Capability.CACHING, "no scenario carries it"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reserved CACHING")
                .hasMessageContaining("no scenario in the suite carries")
                .hasMessageNotContaining("SDK");

        assertThatThrownBy(() -> KnownDeviation.tracked(
                        Capability.LARGE_INTEGERS, "https://example.invalid/1", "cannot be asked"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("LARGE_INTEGERS")
                .hasMessageContaining("the Java SDK cannot express")
                .hasMessageContaining("Client.getIntegerDetails")
                .as("it is not a reservation, and the message must not read as one")
                .hasMessageNotContaining("no scenario in the suite carries");

        // null means a mandatory-scenario gap; ordinary capabilities still allowed
        assertThat(KnownDeviation.untracked(null, "a mandatory gap").capability).isNull();
        assertThat(KnownDeviation.untracked(Capability.DISABLED_FLAGS, "withheld and skipped").capability)
                .isEqualTo("@disabled-flags");
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
    @DisplayName("a backend says what kind it is, and cannot stay silent")
    void backendsSayHowTheyWereDriven() throws NoSuchMethodException {
        assertThat(new InProcessBackendControl().backendType())
                .as("a provider with no backend is controlled in-process, the narrow allowance")
                .isEqualTo(BackendType.IN_PROCESS);

        assertThat(BackendType.HTTP.wireValue()).isEqualTo("http");
        assertThat(BackendType.IN_PROCESS.wireValue()).isEqualTo("in-process");

        // closed set, matching the report schema enum
        assertThat(BackendType.values()).containsExactly(BackendType.HTTP, BackendType.IN_PROCESS);

        // abstract so custom controls must state it
        assertThat(BackendControl.class.getMethod("backendType").isDefault())
                .as("backendType() must be abstract, so that a custom control states it")
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
