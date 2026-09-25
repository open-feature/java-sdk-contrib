package dev.openfeature.contrib.providers.gofeatureflag.e2e;

import static dev.openfeature.contrib.providers.gofeatureflag.e2e.RelayProxyTestHelper.options;
import static dev.openfeature.contrib.providers.gofeatureflag.e2e.RelayProxyTestHelper.provider;

import dev.openfeature.contrib.providers.gofeatureflag.GoFeatureFlagProviderOptions.GoFeatureFlagProviderOptionsBuilder;
import dev.openfeature.contrib.providers.gofeatureflag.bean.EvaluationType;
import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.OpenFeatureAPI;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.testcontainers.containers.GenericContainer;

/** Gives each test a provider domain of its own, and shuts its provider down once it is done. */
abstract class AbstractRelayProxyIntegrationTest {
    protected String domain;

    @BeforeEach
    void beforeEach(TestInfo testInfo) {
        this.domain = testInfo.getDisplayName();
    }

    @AfterEach
    void afterEach() {
        OpenFeatureAPI.getInstance().shutdown();
    }

    @SneakyThrows
    protected Client client(final GoFeatureFlagProviderOptionsBuilder options) {
        OpenFeatureAPI.getInstance().setProviderAndWait(domain, provider(options));
        return OpenFeatureAPI.getInstance().getClient(domain);
    }

    protected Client client(final EvaluationType type, final GenericContainer<?> relayProxy, final String apiKey) {
        return client(options(type, relayProxy).apiKey(apiKey));
    }
}
