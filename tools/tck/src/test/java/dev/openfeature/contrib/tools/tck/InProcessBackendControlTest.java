package dev.openfeature.contrib.tools.tck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.exceptions.TypeMismatchError;
import dev.openfeature.sdk.providers.memory.InMemoryProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Asserts {@link InProcessBackendControl} fails loudly on unsupported operations and isolates scenarios. */
class InProcessBackendControlTest {

    @Test
    @DisplayName("connection operations throw rather than silently doing nothing")
    void connectionOperationsThrow() {
        InProcessBackendControl control = new InProcessBackendControl();

        // message must name the fix
        assertThatThrownBy(control::disconnect)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("does not support 'disconnect'")
                .hasMessageContaining("test-configuration bug")
                .hasMessageContaining("Capability.STALE");

        assertThatThrownBy(control::reconnect)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("does not support 'reconnect'");
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

        // next scenario must not inherit the change
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

        // FLAG_NOT_FOUND scenario relies on its absence
        assertThatThrownBy(() -> provider.getStringEvaluation("missing-flag", "fallback", new ImmutableContext()))
                .hasMessageContaining("missing-flag");
    }

    @Test
    @DisplayName("the canonical flag set keeps the values and types the scenarios depend on")
    void canonicalFlagsKeepTheirValuesAndTypes() throws Exception {
        InMemoryProvider provider = new InProcessBackendControl().createProvider();
        provider.initialize(new ImmutableContext());
        ImmutableContext context = new ImmutableContext();

        // must be a double, or the lossless-coercion scenario passes trivially
        assertThat(provider.getDoubleEvaluation("integral-float-flag", 0.1, context)
                        .getValue())
                .as("integral-float-flag is a Double")
                .isEqualTo(10.0);
        // why the self-tests withhold NUMERIC_COERCION
        assertThatThrownBy(() -> provider.getIntegerEvaluation("integral-float-flag", 1, context))
                .isInstanceOf(TypeMismatchError.class);

        assertThat(provider.getIntegerEvaluation("large-integer-flag", 1, context)
                        .getValue())
                .isEqualTo(2147483647);

        // each default differs from the resolved value
        assertThat(provider.getBooleanEvaluation("boolean-zero-flag", true, context)
                        .getValue())
                .isFalse();
        assertThat(provider.getIntegerEvaluation("integer-zero-flag", 1, context)
                        .getValue())
                .isZero();
        assertThat(provider.getStringEvaluation("string-zero-flag", "fallback", context)
                        .getValue())
                .isEmpty();
    }

    private static String resolveChangingFlag(InMemoryProvider provider) {
        ProviderEvaluation<String> evaluation =
                provider.getStringEvaluation("changing-flag", "unset", new ImmutableContext());
        return evaluation.getValue();
    }
}
