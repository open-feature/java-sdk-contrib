package dev.openfeature.contrib.providers.gofeatureflag.e2e;

import static dev.openfeature.contrib.providers.gofeatureflag.e2e.RelayProxyTestHelper.MUTABLE_FLAG;
import static dev.openfeature.contrib.providers.gofeatureflag.e2e.RelayProxyTestHelper.mutableFlagValue;
import static dev.openfeature.contrib.providers.gofeatureflag.e2e.RelayProxyTestHelper.mutableRelayProxy;
import static dev.openfeature.contrib.providers.gofeatureflag.e2e.RelayProxyTestHelper.options;
import static dev.openfeature.contrib.providers.gofeatureflag.e2e.RelayProxyTestHelper.serveMutableFlagVariation;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.openfeature.contrib.providers.gofeatureflag.bean.EvaluationType;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Changes a flag on a relay proxy of the test's own, and checks when each evaluation mode sees it. */
class FlagChangeIntegrationTest extends AbstractRelayProxyIntegrationTest {

    @DisplayName("an in-process provider should pick up a flag change by polling the relay proxy")
    @Test
    void anInProcessProviderShouldPickUpAFlagChangeByPollingTheRelayProxy() {
        try (val relayProxy = mutableRelayProxy()) {
            relayProxy.start();
            val client = client(options(EvaluationType.IN_PROCESS, relayProxy).flagChangePollingIntervalMs(500L));
            val flagsChanged = new AtomicReference<List<String>>();
            client.onProviderConfigurationChanged(details -> flagsChanged.set(details.getFlagsChanged()));
            assertEquals("A", mutableFlagValue(client));

            serveMutableFlagVariation(relayProxy, "B");

            await().atMost(Duration.ofSeconds(10))
                    .untilAsserted(() -> assertEquals(List.of(MUTABLE_FLAG), flagsChanged.get()));
            assertEquals("B", mutableFlagValue(client));
        }
    }

    @DisplayName("a remote provider should see a flag change on its next evaluation without polling")
    @Test
    void aRemoteProviderShouldSeeAFlagChangeOnItsNextEvaluationWithoutPolling() {
        try (val relayProxy = mutableRelayProxy()) {
            relayProxy.start();
            val client = client(EvaluationType.REMOTE, relayProxy, null);
            val configurationChanges = new AtomicInteger();
            client.onProviderConfigurationChanged(details -> configurationChanges.incrementAndGet());
            assertEquals("A", mutableFlagValue(client));

            serveMutableFlagVariation(relayProxy, "B");

            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertEquals("B", mutableFlagValue(client)));
            assertEquals(0, configurationChanges.get(), "a remote provider holds no configuration to announce");
        }
    }
}
