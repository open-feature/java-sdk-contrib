package dev.openfeature.contrib.providers.flagd.resolver.process;

import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.LayeredEvaluationContext;
import dev.openfeature.sdk.Value;
import java.util.HashMap;
import java.util.stream.IntStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class WasmResolverTest {
    private static InProcessWasmResolver RESOLVER;

    static {
        System.out.println("Creating InProcessWasmResolver...");
        FlagdOptions options =
                FlagdOptions.builder().offlineFlagSourcePath("test.json").build();
        RESOLVER = new InProcessWasmResolver(options, event -> {});
        System.out.println("InProcessWasmResolver created successfully!");
    }

    private EvaluationContext apictx;

    {
        HashMap<String, Value> ctxData = new HashMap<>();
        IntStream.range(0, 2000).forEach(idx -> {
            // Do something
            ctxData.put("key" + idx, new Value(idx));
        });
        apictx = new ImmutableContext(ctxData);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1})
    public void testWasmResolverInit(int i) throws Exception {
        var ctx = new LayeredEvaluationContext(
                apictx, ImmutableContext.EMPTY, ImmutableContext.EMPTY, ImmutableContext.EMPTY);

        RESOLVER.booleanEvaluation("flag" + i, false, ctx);
    }
}
