package dev.openfeature.contrib.providers.flagd.resolver.process;

import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.resolver.Resolver;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.LayeredEvaluationContext;
import dev.openfeature.sdk.Value;
import java.util.HashMap;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

public class WasmResolverTest {
    private static InProcessWasmResolver RESOLVER;
    private static InProcessResolver IN_PROCESS;

    static {
        System.out.println("Creating InProcessWasmResolver...");
        FlagdOptions options =
                FlagdOptions.builder().offlineFlagSourcePath("test-harness/flags/custom-ops.json").build();
        RESOLVER = new InProcessWasmResolver(options, event -> {});
        IN_PROCESS = new InProcessResolver(options, event -> {});
        try {
            RESOLVER.init();
            IN_PROCESS.init();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        System.out.println("InProcessWasmResolver created successfully!");
    }

    private EvaluationContext apictx;
    private EvaluationContext ctx;

    {
        HashMap<String, Value> ctxData = new HashMap<>();
        IntStream.range(0, 100).forEach(idx -> {
            // Do something
            ctxData.put("key" + idx, new Value(idx));
        });
        apictx = new ImmutableContext(ctxData);
        ctx = new LayeredEvaluationContext(
            apictx, ImmutableContext.EMPTY, ImmutableContext.EMPTY, ImmutableContext.EMPTY);

    }

    @ParameterizedTest
    @MethodSource("resolvers")
    public void testWasmResolverInit(Resolver resolver) throws Exception {
        IntStream.range(0, 1000).forEach((i) ->
                resolver.booleanEvaluation("flag" + i, false, ctx));
    }

    @ParameterizedTest
    @MethodSource("resolvers")
    public void testWasmResolverWithoutCTX(Resolver resolver) throws Exception {
        IntStream.range(0, 1000).forEach((i) ->
                resolver.booleanEvaluation("flag" + i, false, ImmutableContext.EMPTY));

    }

    public static Stream<Arguments> resolvers() {
        return Stream.of(
                Arguments.of(RESOLVER),
                Arguments.of(IN_PROCESS)
        );
    }
}
