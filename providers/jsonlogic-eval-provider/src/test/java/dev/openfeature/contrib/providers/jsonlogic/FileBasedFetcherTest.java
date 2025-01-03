package dev.openfeature.contrib.providers.jsonlogic;

import static org.junit.jupiter.api.Assertions.assertNull;

import java.net.URI;
import org.junit.jupiter.api.Test;

class FileBasedFetcherTest {

    @Test
    public void testNullValueForRule() throws Exception {
        URI uri = this.getClass().getResource("/test-rules.json").toURI();
        FileBasedFetcher f = new FileBasedFetcher(uri);
        assertNull(f.getRuleForKey("malformed"));
    }
}
