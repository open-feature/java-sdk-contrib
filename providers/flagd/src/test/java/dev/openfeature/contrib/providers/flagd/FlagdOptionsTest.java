package dev.openfeature.contrib.providers.flagd;

import dev.openfeature.contrib.providers.flagd.resolver.process.storage.MockConnector;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.Connector;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.SetEnvironmentVariable;
import org.mockito.Mockito;

import java.util.function.Function;

import static dev.openfeature.contrib.providers.flagd.Config.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class FlagdOptionsTest {

    @Test
    void TestDefaults() {
        final FlagdOptions builder = FlagdOptions.builder().build();

        assertEquals(DEFAULT_HOST, builder.getHost());
        assertEquals(DEFAULT_RPC_PORT, Integer.toString(builder.getPort()));
        assertFalse(builder.isTls());
        assertNull(builder.getCertPath());
        assertNull(builder.getSocketPath());
        assertEquals(DEFAULT_CACHE, builder.getCacheType());
        assertEquals(DEFAULT_MAX_CACHE_SIZE, builder.getMaxCacheSize());
        assertNull(builder.getSelector());
        assertNull(builder.getOpenTelemetry());
        assertNull(builder.getCustomConnector());
        assertNull(builder.getOfflineFlagSourcePath());
        assertEquals(Resolver.RPC, builder.getResolverType());
        assertEquals(0, builder.getKeepAlive());
    }

    @Test
    void TestBuilderOptions() {
        OpenTelemetry openTelemetry = Mockito.mock(OpenTelemetry.class);
        Connector connector = new MockConnector(null);

        FlagdOptions flagdOptions = FlagdOptions.builder()
                .host("https://hosted-flagd")
                .port(80)
                .tls(true)
                .certPath("etc/cert/ca.crt")
                .cacheType("lru")
                .maxCacheSize(100)
                .selector("app=weatherApp")
                .offlineFlagSourcePath("some-path")
                .openTelemetry(openTelemetry)
                .customConnector(connector)
                .resolverType(Resolver.IN_PROCESS)
                .targetUri("dns:///localhost:8016")
                .keepAlive(1000)
                .build();

        assertEquals("https://hosted-flagd", flagdOptions.getHost());
        assertEquals(80, flagdOptions.getPort());
        assertTrue(flagdOptions.isTls());
        assertEquals("etc/cert/ca.crt", flagdOptions.getCertPath());
        assertEquals("lru", flagdOptions.getCacheType());
        assertEquals(100, flagdOptions.getMaxCacheSize());
        assertEquals("app=weatherApp", flagdOptions.getSelector());
        assertEquals("some-path", flagdOptions.getOfflineFlagSourcePath());
        assertEquals(openTelemetry, flagdOptions.getOpenTelemetry());
        assertEquals(connector, flagdOptions.getCustomConnector());
        assertEquals(Resolver.IN_PROCESS, flagdOptions.getResolverType());
        assertEquals("dns:///localhost:8016", flagdOptions.getTargetUri());
        assertEquals(1000, flagdOptions.getKeepAlive());
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
    @SetEnvironmentVariable(key = RESOLVER_ENV_VAR, value = RESOLVER_IN_PROCESS)
    void testInProcessProviderFromEnv_noPortConfigured_defaultsToCorrectPort() {
        FlagdOptions flagdOptions = FlagdOptions.builder().build();

        assertThat(flagdOptions.getResolverType()).isEqualTo(Resolver.IN_PROCESS);
        assertThat(flagdOptions.getPort()).isEqualTo(Integer.parseInt(DEFAULT_IN_PROCESS_PORT));
    }

    @Nested
    class TestInProcessProviderFromEnv_keepAliveEnvSet {
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
        FlagdOptions flagdOptions = FlagdOptions.builder()
                .resolverType(Resolver.IN_PROCESS)
                .build();

        assertThat(flagdOptions.getResolverType()).isEqualTo(Resolver.IN_PROCESS);
        assertThat(flagdOptions.getPort()).isEqualTo(Integer.parseInt(DEFAULT_IN_PROCESS_PORT));
    }

    @Test
    @SetEnvironmentVariable(key = RESOLVER_ENV_VAR, value = RESOLVER_IN_PROCESS)
    void testInProcessProviderFromEnv_portConfigured_usesConfiguredPort() {
        FlagdOptions flagdOptions = FlagdOptions.builder()
                .port(1000)
                .build();

        assertThat(flagdOptions.getResolverType()).isEqualTo(Resolver.IN_PROCESS);
        assertThat(flagdOptions.getPort()).isEqualTo(1000);
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
        FlagdOptions flagdOptions = FlagdOptions.builder()
                .resolverType(Resolver.RPC)
                .build();

        assertThat(flagdOptions.getResolverType()).isEqualTo(Resolver.RPC);
        assertThat(flagdOptions.getPort()).isEqualTo(Integer.parseInt(DEFAULT_RPC_PORT));
    }

    @Test
    @SetEnvironmentVariable(key = RESOLVER_ENV_VAR, value = RESOLVER_RPC)
    void testRpcProviderFromEnv_portConfigured_usesConfiguredPort() {
        FlagdOptions flagdOptions = FlagdOptions.builder()
                .port(1534)
                .build();

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
