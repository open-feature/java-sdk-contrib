package dev.openfeature.contrib.tools.providertck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.providers.memory.InMemoryProvider;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards the two properties of {@link InProcessBackendControl} that the Gherkin cannot assert
 * about itself.
 *
 * <p>The first is that unsupported operations <strong>fail loudly</strong>. The whole
 * skipped-by-capability design collapses into false confidence if a connection operation quietly
 * does nothing, and a scenario that never runs cannot prove that it would have failed. These tests
 * call the operations directly.
 *
 * <p>The second is that scenario isolation actually isolates. {@link InMemoryProviderTckTest} would
 * still pass if {@code changeFlag()} leaked into the next scenario, because no scenario evaluates
 * {@code changing-flag} before modifying it.
 */
class InProcessBackendControlTest {

    @Test
    @DisplayName("connection operations throw rather than silently doing nothing")
    void connectionOperationsThrow() {
        InProcessBackendControl control = new InProcessBackendControl();

        // The message has to name the fix, because whoever hits this is looking at a red scenario
        // that reads like a provider defect and is in fact a capability declared in error.
        assertThatThrownBy(control::disconnect)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("does not support 'disconnect'")
                .hasMessageContaining("test-configuration bug")
                .hasMessageContaining("Capability.STALE");

        assertThatThrownBy(control::reconnect)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("does not support 'reconnect'");

        assertThatThrownBy(() -> control.disconnectFor(Duration.ofSeconds(1)))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("does not support 'disconnectFor'");
    }

    @Test
    @DisplayName("changing a flag without a provider fails instead of being lost")
    void changeFlagWithoutProviderThrows() {
        InProcessBackendControl control = new InProcessBackendControl();
        control.prepareScenario();

        assertThatThrownBy(control::changeFlag)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Given a stable provider");
    }

    @Test
    @DisplayName("changeFlag changes the resolved value and the next scenario starts from baseline")
    void changeFlagIsVisibleAndDoesNotLeak() throws Exception {
        InProcessBackendControl control = new InProcessBackendControl();

        control.prepareScenario();
        InMemoryProvider first = control.createProvider();
        first.initialize(new ImmutableContext());
        assertThat(resolveChangingFlag(first)).isEqualTo("foo");

        control.changeFlag();
        assertThat(resolveChangingFlag(first))
                .as("changeFlag must actually change what the provider resolves, not merely emit an event")
                .isEqualTo("bar");

        // The next scenario must not inherit that change. The baseline map is shared between every
        // provider this control hands out, so a mutation that reached it would leak forwards.
        control.prepareScenario();
        InMemoryProvider second = control.createProvider();
        second.initialize(new ImmutableContext());
        assertThat(resolveChangingFlag(second))
                .as("each scenario starts from the canonical baseline")
                .isEqualTo("foo");
    }

    @Test
    @DisplayName("the canonical flag set omits missing-flag")
    void missingFlagIsAbsent() throws Exception {
        InProcessBackendControl control = new InProcessBackendControl();
        InMemoryProvider provider = control.createProvider();
        provider.initialize(new ImmutableContext());

        // Absence is what the FLAG_NOT_FOUND scenario tests, so seeding it by accident would turn
        // that scenario green for the wrong reason.
        assertThatThrownBy(() -> provider.getStringEvaluation("missing-flag", "fallback", new ImmutableContext()))
                .hasMessageContaining("missing-flag");
    }

    private static String resolveChangingFlag(InMemoryProvider provider) {
        ProviderEvaluation<String> evaluation =
                provider.getStringEvaluation("changing-flag", "unset", new ImmutableContext());
        return evaluation.getValue();
    }
}
