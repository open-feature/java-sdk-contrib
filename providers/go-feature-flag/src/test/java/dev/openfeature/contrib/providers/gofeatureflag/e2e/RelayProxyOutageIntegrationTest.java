package dev.openfeature.contrib.providers.gofeatureflag.e2e;

import static dev.openfeature.contrib.providers.gofeatureflag.e2e.RelayProxyTestHelper.MUTABLE_FLAG;
import static dev.openfeature.contrib.providers.gofeatureflag.e2e.RelayProxyTestHelper.mutableFlagValue;
import static dev.openfeature.contrib.providers.gofeatureflag.e2e.RelayProxyTestHelper.mutableRelayProxy;
import static dev.openfeature.contrib.providers.gofeatureflag.e2e.RelayProxyTestHelper.options;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.openfeature.contrib.providers.gofeatureflag.TestUtils;
import dev.openfeature.contrib.providers.gofeatureflag.bean.EvaluationType;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.Reason;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Stops a relay proxy of the test's own once the provider is ready, and checks what each evaluation mode serves. */
class RelayProxyOutageIntegrationTest extends AbstractRelayProxyIntegrationTest {

    @DisplayName("an in-process provider should keep serving its last configuration once the relay proxy is gone")
    @Test
    void anInProcessProviderShouldKeepServingItsLastConfigurationOnceTheRelayProxyIsGone() {
        try (val relayProxy = mutableRelayProxy()) {
            relayProxy.start();
            val client = client(options(EvaluationType.IN_PROCESS, relayProxy).flagChangePollingIntervalMs(200L));
            val stale = new AtomicBoolean();
            client.onProviderStale(details -> stale.set(true));

            relayProxy.stop();

            val got = client.getStringDetails(MUTABLE_FLAG, "sdk-default", TestUtils.defaultEvaluationContext);
            assertEquals("A", got.getValue());
            assertNull(got.getErrorCode());
            await().atMost(Duration.ofSeconds(10)).untilTrue(stale);
            assertEquals("A", mutableFlagValue(client), "a stale configuration is still served");
        }
    }

    @DisplayName("a remote provider should fail its evaluations once the relay proxy is gone")
    @Test
    void aRemoteProviderShouldFailItsEvaluationsOnceTheRelayProxyIsGone() {
        try (val relayProxy = mutableRelayProxy()) {
            relayProxy.start();
            val client = client(options(EvaluationType.REMOTE, relayProxy).timeout(1000));
            assertEquals("A", mutableFlagValue(client));

            relayProxy.stop();

            val got = client.getStringDetails(MUTABLE_FLAG, "sdk-default", TestUtils.defaultEvaluationContext);
            assertEquals("sdk-default", got.getValue());
            assertEquals(ErrorCode.GENERAL, got.getErrorCode());
            assertEquals(Reason.ERROR.name(), got.getReason());
        }
    }
}
