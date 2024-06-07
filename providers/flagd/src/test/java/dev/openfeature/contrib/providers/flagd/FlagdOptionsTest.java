package dev.openfeature.contrib.providers.flagd;

import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
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
        assertEquals(DEFAULT_MAX_EVENT_STREAM_RETRIES, builder.getMaxEventStreamRetries());
        assertNull(builder.getSelector());
        assertNull(builder.getOpenTelemetry());
        assertNull(builder.getOfflineFlagSourcePath());
        assertEquals(Resolver.RPC, builder.getResolverType());
    }

    @Test
    void TestBuilderOptions() {
        OpenTelemetry openTelemetry = Mockito.mock(OpenTelemetry.class);

        FlagdOptions flagdOptions = FlagdOptions.builder()
                .host("https://hosted-flagd")
                .port(80)
                .tls(true)
                .certPath("etc/cert/ca.crt")
                .cacheType("lru")
                .maxCacheSize(100)
                .maxEventStreamRetries(1)
                .selector("app=weatherApp")
                .offlineFlagSourcePath("some-path")
                .openTelemetry(openTelemetry)
                .resolverType(Resolver.IN_PROCESS)
                .build();

        assertEquals("https://hosted-flagd", flagdOptions.getHost());
        assertEquals(80, flagdOptions.getPort());
        assertTrue(flagdOptions.isTls());
        assertEquals("etc/cert/ca.crt", flagdOptions.getCertPath());
        assertEquals("lru", flagdOptions.getCacheType());
        assertEquals(100, flagdOptions.getMaxCacheSize());
        assertEquals(1, flagdOptions.getMaxEventStreamRetries());
        assertEquals("app=weatherApp", flagdOptions.getSelector());
        assertEquals("some-path", flagdOptions.getOfflineFlagSourcePath());
        assertEquals(openTelemetry, flagdOptions.getOpenTelemetry());
        assertEquals(Resolver.IN_PROCESS, flagdOptions.getResolverType());
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
    @Disabled("Currently there is no defined way on how to set environment variables for tests")
    void testInProcessProviderFromEnv_noPortConfigured_defaultsToCorrectPort() {
        FlagdOptions flagdOptions = FlagdOptions.builder().build();

        assertThat(flagdOptions.getResolverType()).isEqualTo(Resolver.IN_PROCESS);
        assertThat(flagdOptions.getPort()).isEqualTo(Integer.parseInt(DEFAULT_IN_PROCESS_PORT));
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
    @Disabled("Currently there is no defined way on how to set environment variables for tests")
    void testInProcessProviderFromEnv_portConfigured_usesConfiguredPort() {
        FlagdOptions flagdOptions = FlagdOptions.builder()
                .port(1000)
                .build();

        assertThat(flagdOptions.getResolverType()).isEqualTo(Resolver.IN_PROCESS);
        assertThat(flagdOptions.getPort()).isEqualTo(1000);
    }

    @Test
    @Disabled("Currently there is no defined way on how to set environment variables for tests")
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
    @Disabled("Currently there is no defined way on how to set environment variables for tests")
    void testRpcProviderFromEnv_portConfigured_usesConfiguredPort() {
        FlagdOptions flagdOptions = FlagdOptions.builder()
                .port(1534)
                .build();

        assertThat(flagdOptions.getResolverType()).isEqualTo(Resolver.RPC);
        assertThat(flagdOptions.getPort()).isEqualTo(1534);

    }
}
