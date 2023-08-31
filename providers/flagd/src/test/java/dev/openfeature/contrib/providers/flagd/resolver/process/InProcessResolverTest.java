package dev.openfeature.contrib.providers.flagd.resolver.process;

import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import org.junit.jupiter.api.Test;

class InProcessResolverTest {

    @Test
    public void testInitializer() throws Exception {
        FlagdOptions options = FlagdOptions.builder()
                .host("localhost")
                .port(8080)
                .build();

        InProcessResolver resolver = new InProcessResolver(options);

        resolver.init();

        Thread.sleep(10000);
    }

}