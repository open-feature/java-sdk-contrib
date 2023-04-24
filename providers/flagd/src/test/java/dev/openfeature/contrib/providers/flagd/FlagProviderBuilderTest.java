package dev.openfeature.contrib.providers.flagd;

import org.junit.jupiter.api.Test;

import static dev.openfeature.contrib.providers.flagd.Config.DEFAULT_CACHE;
import static dev.openfeature.contrib.providers.flagd.Config.DEFAULT_HOST;
import static dev.openfeature.contrib.providers.flagd.Config.DEFAULT_MAX_CACHE_SIZE;
import static dev.openfeature.contrib.providers.flagd.Config.DEFAULT_MAX_EVENT_STREAM_RETRIES;
import static dev.openfeature.contrib.providers.flagd.Config.DEFAULT_PORT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

public class FlagProviderBuilderTest {

    @Test
    public void TestDefaults() {
        final FlagdOptions builder = FlagdOptions.builder().build();

        assertEquals(builder.getHost(),DEFAULT_HOST );
        assertEquals(Integer.toString(builder.getPort()),DEFAULT_PORT );
        assertFalse(builder.isTls());
        assertNull(builder.getCertPath());
        assertNull(builder.getSocketPath());
        assertEquals(builder.getCacheType(),DEFAULT_CACHE );
        assertEquals(builder.getMaxCacheSize(), DEFAULT_MAX_CACHE_SIZE);
        assertEquals(builder.getMaxEventStreamRetries(), DEFAULT_MAX_EVENT_STREAM_RETRIES);
        assertNull(builder.getTelemetrySdk());
    }
}
