package dev.openfeature.contrib.providers.jsonlogic;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertNull;

class FileBasedFetcherTest {

    @Test public void testNullValueForRule() throws Exception {
        URI uri = this.getClass().getResource("/test-rules.json").toURI();
        FileBasedFetcher f = new FileBasedFetcher(uri);
        assertNull(f.getRuleForKey("malformed"));
    }
}