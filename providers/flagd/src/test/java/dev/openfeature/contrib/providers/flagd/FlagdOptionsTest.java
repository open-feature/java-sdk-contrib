package dev.openfeature.contrib.providers.flagd;

import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

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

        assertEquals(builder.getHost(), DEFAULT_HOST);
        assertEquals(Integer.toString(builder.getPort()), DEFAULT_PORT);
        assertFalse(builder.isTls());
        assertNull(builder.getCertPath());
        assertNull(builder.getSocketPath());
        assertEquals(builder.getCacheType(), DEFAULT_CACHE);
        assertEquals(builder.getMaxCacheSize(), DEFAULT_MAX_CACHE_SIZE);
        assertEquals(builder.getMaxEventStreamRetries(), DEFAULT_MAX_EVENT_STREAM_RETRIES);
        assertNull(builder.getSelector());
        assertNull(builder.getOpenTelemetry());
        assertNull(builder.getOfflineFlagSourcePath());
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
                .build();

        assertEquals(flagdOptions.getHost(), "https://hosted-flagd");
        assertEquals(flagdOptions.getPort(), 80);
        assertTrue(flagdOptions.isTls());
        assertEquals(flagdOptions.getCertPath(), "etc/cert/ca.crt");
        assertEquals(flagdOptions.getCacheType(), "lru");
        assertEquals(flagdOptions.getMaxCacheSize(), 100);
        assertEquals(flagdOptions.getMaxEventStreamRetries(), 1);
        assertEquals(flagdOptions.getSelector(), "app=weatherApp");
        assertEquals("some-path", flagdOptions.getOfflineFlagSourcePath());
        assertEquals(flagdOptions.getOpenTelemetry(), openTelemetry);
    }
}
