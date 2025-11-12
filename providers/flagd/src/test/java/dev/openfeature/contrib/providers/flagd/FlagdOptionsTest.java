package dev.openfeature.contrib.providers.flagd;

import static dev.openfeature.contrib.providers.flagd.Config.DEFAULT_CACHE;
import static dev.openfeature.contrib.providers.flagd.Config.DEFAULT_HOST;
import static dev.openfeature.contrib.providers.flagd.Config.DEFAULT_IN_PROCESS_PORT;
import static dev.openfeature.contrib.providers.flagd.Config.DEFAULT_MAX_CACHE_SIZE;
import static dev.openfeature.contrib.providers.flagd.Config.DEFAULT_RPC_PORT;
import static dev.openfeature.contrib.providers.flagd.Config.KEEP_ALIVE_MS_ENV_VAR_NAME;
import static dev.openfeature.contrib.providers.flagd.Config.KEEP_ALIVE_MS_ENV_VAR_NAME_OLD;
import static dev.openfeature.contrib.providers.flagd.Config.RESOLVER_ENV_VAR;
import static dev.openfeature.contrib.providers.flagd.Config.RESOLVER_IN_PROCESS;
import static dev.openfeature.contrib.providers.flagd.Config.RESOLVER_RPC;
import static dev.openfeature.contrib.providers.flagd.Config.TARGET_URI_ENV_VAR_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openfeature.contrib.providers.flagd.Config.Resolver;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.MockConnector;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.QueueSource;
import io.grpc.ClientInterceptor;
import io.opentelemetry.api.OpenTelemetry;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.SetEnvironmentVariable;
import org.mockito.Mockito;

class FlagdOptionsTest {

    @Test
    void testDefaults() {
        final FlagdOptions builder = FlagdOptions.builder().build();

        assertEquals(DEFAULT_HOST, builder.getHost());
        assertEquals(DEFAULT_RPC_PORT, Integer.toString(builder.getPort()));
        assertFalse(builder.isTls());
        assertNull(builder.getCertPath());
        assertNull(builder.getSocketPath());
        assertEquals(DEFAULT_CACHE, builder.getCacheType());
        assertEquals(DEFAULT_MAX_CACHE_SIZE, builder.getMaxCacheSize());
        assertNull(builder.getSelector());
        assertNull(builder.getProviderId());
        assertNull(builder.getOpenTelemetry());
        assertNull(builder.getCustomConnector());
        assertNull(builder.getOfflineFlagSourcePath());
        assertEquals(Resolver.RPC, builder.getResolverType());
        assertEquals(0, builder.getKeepAlive());
        assertNull(builder.getDefaultAuthority());
        assertNull(builder.getClientInterceptors());
    }

    @Test
    void testBuilderOptions() {
        OpenTelemetry openTelemetry = Mockito.mock(OpenTelemetry.class);
        QueueSource connector = new MockConnector(null);
        List<ClientInterceptor> clientInterceptors = new ArrayList<ClientInterceptor>();

        FlagdOptions flagdOptions = FlagdOptions.builder()
                .host("https://hosted-flagd")
                .port(80)
                .tls(true)
                .certPath("etc/cert/ca.crt")
                .cacheType("lru")
                .maxCacheSize(100)
                .selector("app=weatherApp")
                .providerId("test/provider/id_1")
                .openTelemetry(openTelemetry)
                .customConnector(connector)
                .resolverType(Resolver.IN_PROCESS)
                .targetUri("dns:///localhost:8016")
                .keepAlive(1000)
                .defaultAuthority("test-authority.sync.example.com")
                .clientInterceptors(clientInterceptors)
                .build();

        assertEquals("https://hosted-flagd", flagdOptions.getHost());
        assertEquals(80, flagdOptions.getPort());
        assertTrue(flagdOptions.isTls());
        assertEquals("etc/cert/ca.crt", flagdOptions.getCertPath());
        assertEquals("lru", flagdOptions.getCacheType());
        assertEquals(100, flagdOptions.getMaxCacheSize());
        assertEquals("app=weatherApp", flagdOptions.getSelector());
        assertEquals("test/provider/id_1", flagdOptions.getProviderId());
        assertEquals(openTelemetry, flagdOptions.getOpenTelemetry());
        assertEquals(connector, flagdOptions.getCustomConnector());
        assertEquals(Resolver.IN_PROCESS, flagdOptions.getResolverType());
        assertEquals("dns:///localhost:8016", flagdOptions.getTargetUri());
        assertEquals(1000, flagdOptions.getKeepAlive());
        assertEquals("test-authority.sync.example.com", flagdOptions.getDefaultAuthority());
        assertEquals(clientInterceptors, flagdOptions.getClientInterceptors());
    }

    @Test
    void testValueProviderForEdgeCase_valid() {
        Function<String, String> valueProvider = s -> "in-process";
        assertEquals(Resolver.IN_PROCESS, Config.fromValueProvider(valueProvider));

        valueProvider = s -> "IN-PROCESS";
        assertEquals(Resolver.IN_PROCESS, Config.fromValueProvider(valueProvider));

        valueProvider = s -> "rpc";
        assertEquals(Resolver.RPC, Config.fromValueProvider(valueProvider));

        valueProvider = s -> "RPC";
        assertEquals(Resolver.RPC, Config.fromValueProvider(valueProvider));
    }

    @Test
    void testValueProviderForEdgeCase_invalid() {
        Function<String, String> dummy = s -> "some-other";
        assertEquals(Config.DEFAULT_RESOLVER_TYPE, Config.fromValueProvider(dummy));

        dummy = s -> null;
        assertEquals(Config.DEFAULT_RESOLVER_TYPE, Config.fromValueProvider(dummy));
    }

    @Test
    void toBuilder_maintains_props() {
        String selector = "some-selector";
        int port = 1337;
        int gracePeriod = 33;
        int keepAlive = 9000;

        FlagdOptions options = FlagdOptions.builder()
                .resolverType(Resolver.IN_PROCESS)
                .port(port)
                .selector(selector)
                .keepAlive(keepAlive)
                .build();

        FlagdOptions rebuiltOptions =
                options.toBuilder().retryGracePeriod(gracePeriod).build();

        // old props
        assertEquals(selector, rebuiltOptions.getSelector());
        assertEquals(port, rebuiltOptions.getPort());
        assertEquals(keepAlive, rebuiltOptions.getKeepAlive());
        assertEquals(Resolver.IN_PROCESS, rebuiltOptions.getResolverType());

        // added props
        assertEquals(gracePeriod, rebuiltOptions.getRetryGracePeriod());
    }

    @Test
    @SetEnvironmentVariable(key = RESOLVER_ENV_VAR, value = RESOLVER_IN_PROCESS)
    void testInProcessProviderFromEnv_noPortConfigured_defaultsToCorrectPort() {
        FlagdOptions flagdOptions = FlagdOptions.builder().build();

        assertThat(flagdOptions.getResolverType()).isEqualTo(Resolver.IN_PROCESS);
        assertThat(flagdOptions.getPort()).isEqualTo(Integer.parseInt(DEFAULT_IN_PROCESS_PORT));
    }

    @Nested
    class TestInProcessProviderFromEnvkeepAliveEnvSet {
        @Test
        @SetEnvironmentVariable(key = KEEP_ALIVE_MS_ENV_VAR_NAME, value = "1336")
        void usesSet() {
            FlagdOptions flagdOptions = FlagdOptions.builder().build();

            assertThat(flagdOptions.getKeepAlive()).isEqualTo(1336);
        }

        @Test
        @SetEnvironmentVariable(key = KEEP_ALIVE_MS_ENV_VAR_NAME_OLD, value = "1337")
        void usesSetOldName() {
            FlagdOptions flagdOptions = FlagdOptions.builder().build();

            assertThat(flagdOptions.getKeepAlive()).isEqualTo(1337);
        }

        @Test
        @SetEnvironmentVariable(key = KEEP_ALIVE_MS_ENV_VAR_NAME_OLD, value = "2222")
        @SetEnvironmentVariable(key = KEEP_ALIVE_MS_ENV_VAR_NAME, value = "1338")
        void usesSetOldAndNewName() {
            FlagdOptions flagdOptions = FlagdOptions.builder().build();

            assertThat(flagdOptions.getKeepAlive()).isEqualTo(1338);
        }
    }

    @Test
    void testInProcessProvider_noPortConfigured_defaultsToCorrectPort() {
        FlagdOptions flagdOptions =
                FlagdOptions.builder().resolverType(Resolver.IN_PROCESS).build();

        assertThat(flagdOptions.getResolverType()).isEqualTo(Resolver.IN_PROCESS);
        assertThat(flagdOptions.getPort()).isEqualTo(Integer.parseInt(DEFAULT_IN_PROCESS_PORT));
    }

    @Test
    @SetEnvironmentVariable(key = RESOLVER_ENV_VAR, value = RESOLVER_IN_PROCESS)
    void testInProcessProviderFromEnv_portConfigured_usesConfiguredPort() {
        FlagdOptions flagdOptions = FlagdOptions.builder().port(1000).build();

        assertThat(flagdOptions.getResolverType()).isEqualTo(Resolver.IN_PROCESS);
        assertThat(flagdOptions.getPort()).isEqualTo(1000);
    }

    @Test
    @SetEnvironmentVariable(key = RESOLVER_ENV_VAR, value = RESOLVER_IN_PROCESS)
    @SetEnvironmentVariable(key = "FLAGD_SYNC_PORT", value = "1005")
    void testInProcessProvider_usesSyncPortEnvVarWhenSet() {
        FlagdOptions flagdOptions = FlagdOptions.builder().build();

        assertThat(flagdOptions.getResolverType()).isEqualTo(Resolver.IN_PROCESS);
        assertThat(flagdOptions.getPort()).isEqualTo(1005);
    }

    @Test
    @SetEnvironmentVariable(key = RESOLVER_ENV_VAR, value = RESOLVER_IN_PROCESS)
    @SetEnvironmentVariable(key = "FLAGD_PORT", value = "5000")
    void testInProcessProvider_fallsBackToFlagdPortWhenSyncPortNotSet(){
        FlagdOptions flagdOptions = FlagdOptions.builder().build();

        assertThat(flagdOptions.getResolverType()).isEqualTo(Resolver.IN_PROCESS);
        assertThat(flagdOptions.getPort()).isEqualTo(5000);
    }


    @Test
    @SetEnvironmentVariable(key = RESOLVER_ENV_VAR, value = RESOLVER_RPC)
    void testRpcProviderFromEnv_noPortConfigured_defaultsToCorrectPort() {
        FlagdOptions flagdOptions = FlagdOptions.builder().build();

        assertThat(flagdOptions.getResolverType()).isEqualTo(Resolver.RPC);
        assertThat(flagdOptions.getPort()).isEqualTo(Integer.parseInt(DEFAULT_RPC_PORT));
    }

    @Test
    void testRpcProvider_noPortConfigured_defaultsToCorrectPort() {
        FlagdOptions flagdOptions =
                FlagdOptions.builder().resolverType(Resolver.RPC).build();

        assertThat(flagdOptions.getResolverType()).isEqualTo(Resolver.RPC);
        assertThat(flagdOptions.getPort()).isEqualTo(Integer.parseInt(DEFAULT_RPC_PORT));
    }

    @Test
    @SetEnvironmentVariable(key = RESOLVER_ENV_VAR, value = RESOLVER_RPC)
    void testRpcProviderFromEnv_portConfigured_usesConfiguredPort() {
        FlagdOptions flagdOptions = FlagdOptions.builder().port(1534).build();

        assertThat(flagdOptions.getResolverType()).isEqualTo(Resolver.RPC);
        assertThat(flagdOptions.getPort()).isEqualTo(1534);
    }

    @Test
    @SetEnvironmentVariable(key = TARGET_URI_ENV_VAR_NAME, value = "envoy://localhost:1234/foo.service")
    void testTargetOverrideFromEnv() {
        FlagdOptions flagdOptions = FlagdOptions.builder().build();

        assertThat(flagdOptions.getTargetUri()).isEqualTo("envoy://localhost:1234/foo.service");
    }
}
