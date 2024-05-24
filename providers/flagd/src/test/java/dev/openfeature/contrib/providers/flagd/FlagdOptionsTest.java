package dev.openfeature.contrib.providers.flagd;

import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.function.Function;

import static dev.openfeature.contrib.providers.flagd.Config.DEFAULT_CACHE;
import static dev.openfeature.contrib.providers.flagd.Config.DEFAULT_HOST;
import static dev.openfeature.contrib.providers.flagd.Config.DEFAULT_MAX_CACHE_SIZE;
import static dev.openfeature.contrib.providers.flagd.Config.DEFAULT_MAX_EVENT_STREAM_RETRIES;
import static dev.openfeature.contrib.providers.flagd.Config.DEFAULT_PORT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FlagdOptionsTest {

    @Test
    public void TestDefaults() {
        final FlagdOptions builder = FlagdOptions.builder().build();

        assertEquals(DEFAULT_HOST, builder.getHost());
        assertEquals(DEFAULT_PORT, Integer.toString(builder.getPort()));
        assertFalse(builder.isTls());
        assertNull(builder.getCertPath());
        assertNull(builder.getSocketPath());
        assertEquals(DEFAULT_CACHE, builder.getCacheType());
        assertEquals(DEFAULT_MAX_CACHE_SIZE, builder.getMaxCacheSize());
        assertEquals(DEFAULT_MAX_EVENT_STREAM_RETRIES, builder.getMaxEventStreamRetries());
        assertNull(builder.getSelector());
        assertNull(builder.getOpenTelemetry());
        assertNull(builder.getOfflineFlagSourcePath());
        assertEquals(Config.Evaluator.RPC, builder.getResolverType());
    }

    @Test
    public void TestBuilderOptions() {
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
                .resolverType(Config.Evaluator.IN_PROCESS)
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
        assertEquals(Config.Evaluator.IN_PROCESS, flagdOptions.getResolverType());
    }


    @Test
    public void testValueProviderForEdgeCase_valid() {
        Function<String, String> valueProvider = s -> "in-process";
        assertEquals(Config.Evaluator.IN_PROCESS, Config.fromValueProvider(valueProvider));

        valueProvider = s -> "IN-PROCESS";
        assertEquals(Config.Evaluator.IN_PROCESS, Config.fromValueProvider(valueProvider));

        valueProvider = s -> "rpc";
        assertEquals(Config.Evaluator.RPC, Config.fromValueProvider(valueProvider));

        valueProvider = s -> "RPC";
        assertEquals(Config.Evaluator.RPC, Config.fromValueProvider(valueProvider));
    }

    @Test
    public void testValueProviderForEdgeCase_invalid() {
        Function<String, String> dummy = s -> "some-other";
        assertEquals(Config.DEFAULT_RESOLVER_TYPE, Config.fromValueProvider(dummy));

        dummy = s -> null;
        assertEquals(Config.DEFAULT_RESOLVER_TYPE, Config.fromValueProvider(dummy));
    }

}
