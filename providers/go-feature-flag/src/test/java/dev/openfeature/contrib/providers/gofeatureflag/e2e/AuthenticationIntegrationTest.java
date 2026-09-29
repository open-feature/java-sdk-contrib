package dev.openfeature.contrib.providers.gofeatureflag.e2e;

import static dev.openfeature.contrib.providers.gofeatureflag.e2e.RelayProxyTestHelper.options;
import static dev.openfeature.contrib.providers.gofeatureflag.e2e.RelayProxyTestHelper.provider;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.openfeature.contrib.providers.gofeatureflag.TestUtils;
import dev.openfeature.contrib.providers.gofeatureflag.bean.EvaluationType;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.Reason;
import dev.openfeature.sdk.exceptions.FatalError;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Runs the provider against a relay proxy that only serves callers sending the authorized_token API key. */
@Testcontainers
class AuthenticationIntegrationTest extends AbstractRelayProxyIntegrationTest {
    @Container
    private static final GenericContainer<?> authenticatedRelayProxy = RelayProxyTestHelper.authenticatedRelayProxy();

    @DisplayName("a relay proxy that requires an API key should serve a provider that sends it")
    @ParameterizedTest(name = "{0} evaluation")
    @EnumSource(EvaluationType.class)
    void aRelayProxyThatRequiresAnApiKeyShouldServeAProviderThatSendsIt(EvaluationType type) {
        val client = client(type, authenticatedRelayProxy, "authorized_token");

        val got = client.getBooleanDetails("bool_targeting_match", false, TestUtils.defaultEvaluationContext);

        assertEquals(true, got.getValue());
        assertEquals("True", got.getVariant(), "the relay proxy accepted the key and evaluated the flag");
    }

    @DisplayName("an in-process provider with a rejected API key should fail its initialization")
    @Test
    void anInProcessProviderWithARejectedApiKeyShouldFailItsInitialization() {
        val provider = provider(
                options(EvaluationType.IN_PROCESS, authenticatedRelayProxy).apiKey("wrong_token"));

        assertThrows(FatalError.class, () -> OpenFeatureAPI.getInstance().setProviderAndWait(domain, provider));

        val got = OpenFeatureAPI.getInstance()
                .getClient(domain)
                .getBooleanDetails("bool_targeting_match", false, TestUtils.defaultEvaluationContext);
        assertEquals(false, got.getValue());
        assertEquals(ErrorCode.PROVIDER_FATAL, got.getErrorCode());
    }

    @DisplayName("a remote provider with a rejected API key should fail its first evaluation")
    @Test
    void aRemoteProviderWithARejectedApiKeyShouldFailItsFirstEvaluation() {
        val client = client(EvaluationType.REMOTE, authenticatedRelayProxy, "wrong_token");

        val got = client.getBooleanDetails("bool_targeting_match", false, TestUtils.defaultEvaluationContext);

        assertEquals(false, got.getValue());
        assertEquals(ErrorCode.GENERAL, got.getErrorCode());
        assertEquals(Reason.ERROR.name(), got.getReason());
    }
}
