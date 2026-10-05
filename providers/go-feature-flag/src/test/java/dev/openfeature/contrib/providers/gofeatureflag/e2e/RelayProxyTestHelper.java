package dev.openfeature.contrib.providers.gofeatureflag.e2e;

import dev.openfeature.contrib.providers.gofeatureflag.GoFeatureFlagProvider;
import dev.openfeature.contrib.providers.gofeatureflag.GoFeatureFlagProviderOptions;
import dev.openfeature.contrib.providers.gofeatureflag.GoFeatureFlagProviderOptions.GoFeatureFlagProviderOptionsBuilder;
import dev.openfeature.contrib.providers.gofeatureflag.TestUtils;
import dev.openfeature.contrib.providers.gofeatureflag.bean.EvaluationType;
import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.Value;
import lombok.SneakyThrows;
import lombok.val;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/** Builds the relay proxies the end-to-end tests run against, and the providers that talk to them. */
final class RelayProxyTestHelper {
    static final String MUTABLE_FLAG = "mutable_flag";
    static final String TARGETING_KEY = "d45e303a-38c2-11ed-a261-0242ac120002";

    private static final DockerImageName RELAY_PROXY_IMAGE =
            DockerImageName.parse("gofeatureflag/go-feature-flag:latest");
    private static final String FLAGS_PATH = "/openfeature/provider_tests/flags.yaml";

    private RelayProxyTestHelper() {}

    /** A relay proxy serving the provider_tests fixture, with its evaluation context enrichment. */
    static GenericContainer<?> relayProxy() {
        return relayProxy(MountableFile.forClasspathResource("provider_tests/flags.yaml"), "goff-proxy.yaml", 1031);
    }

    /** A relay proxy serving the provider_tests fixture only to callers sending the authorized_token API key. */
    static GenericContainer<?> authenticatedRelayProxy() {
        return relayProxy(
                MountableFile.forClasspathResource("provider_tests/flags.yaml"), "goff-proxy-authenticated.yaml", 1032);
    }

    /** A relay proxy serving only {@link #MUTABLE_FLAG} on variation A, for tests that change or stop it. */
    static GenericContainer<?> mutableRelayProxy() {
        return relayProxy(Transferable.of(mutableFlag("A")), "goff-proxy.yaml", 1031);
    }

    /** Rewrites the flag file of a {@link #mutableRelayProxy()} so that it serves the given variation. */
    static void serveMutableFlagVariation(final GenericContainer<?> relayProxy, final String variation) {
        relayProxy.copyFileToContainer(Transferable.of(mutableFlag(variation)), FLAGS_PATH);
    }

    static GoFeatureFlagProviderOptionsBuilder options(
            final EvaluationType type, final GenericContainer<?> relayProxy) {
        return GoFeatureFlagProviderOptions.builder()
                .endpoint(endpoint(relayProxy))
                .evaluationType(type);
    }

    @SneakyThrows
    static GoFeatureFlagProvider provider(final GoFeatureFlagProviderOptionsBuilder options) {
        return new GoFeatureFlagProvider(options.build());
    }

    static String mutableFlagValue(final Client client) {
        return client.getStringValue(MUTABLE_FLAG, "sdk-default", TestUtils.defaultEvaluationContext);
    }

    /** Evaluates the flag with the canonical context, through the resolver matching the default value's type. */
    static FlagEvaluationDetails<?> evaluate(final Client client, final String flagKey, final Object defaultValue) {
        val ctx = TestUtils.defaultEvaluationContext;
        if (defaultValue instanceof Boolean) {
            return client.getBooleanDetails(flagKey, (Boolean) defaultValue, ctx);
        }
        if (defaultValue instanceof String) {
            return client.getStringDetails(flagKey, (String) defaultValue, ctx);
        }
        if (defaultValue instanceof Integer) {
            return client.getIntegerDetails(flagKey, (Integer) defaultValue, ctx);
        }
        if (defaultValue instanceof Double) {
            return client.getDoubleDetails(flagKey, (Double) defaultValue, ctx);
        }
        return client.getObjectDetails(flagKey, (Value) defaultValue, ctx);
    }

    private static GenericContainer<?> relayProxy(
            final Transferable flags, final String configuration, final int port) {
        return new GenericContainer<>(RELAY_PROXY_IMAGE)
                .withCopyToContainer(flags, FLAGS_PATH)
                .withCopyFileToContainer(
                        MountableFile.forClasspathResource("provider_tests/" + configuration), "/" + configuration)
                .withCommand("/go-feature-flag", "--config", "/" + configuration)
                .withExposedPorts(port)
                .waitingFor(Wait.forHttp("/health").forStatusCode(200));
    }

    private static String mutableFlag(final String variation) {
        return MUTABLE_FLAG + ":\n"
                + "  variations:\n"
                + "    A: A\n"
                + "    B: B\n"
                + "  defaultRule:\n"
                + "    variation: " + variation + "\n";
    }

    private static String endpoint(final GenericContainer<?> relayProxy) {
        val port = relayProxy.getExposedPorts().get(0);
        return "http://" + relayProxy.getHost() + ":" + relayProxy.getMappedPort(port) + "/";
    }
}
