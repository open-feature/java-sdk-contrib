package dev.openfeature.contrib.providers.flagd.resolver.process;

import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import org.junit.jupiter.api.Test;

public class WasmResolverTest {

    @Test
    public void testWasmResolverInit() throws Exception {
        System.out.println("Creating InProcessWasmResolver...");
        FlagdOptions options = FlagdOptions.builder()
            .offlineFlagSourcePath("test.json")
            .build();

        InProcessWasmResolver resolver = new InProcessWasmResolver(options, event -> {});
        System.out.println("InProcessWasmResolver created successfully!");
        resolver.init();
    }
}
