package dev.openfeature.contrib.providers.flagd.resolver.grpc.strategy;

import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResolveFactoryTest {

    @Test
    public void simpleResolverAsDefault() {
        // given
        final FlagdOptions options = FlagdOptions.builder().build();

        // when
        final ResolveStrategy strategy = ResolveFactory.getStrategy(options);

        // then
        assertEquals(SimpleResolving.class, strategy.getClass());
    }


    @Test
    public void tracedResolverWhenOTelSdkIsSet() {
        // given
        final OpenTelemetry telemetry = Mockito.mock(OpenTelemetry.class);

        final FlagdOptions options = FlagdOptions.builder().openTelemetry(telemetry).build();

        // when
        final ResolveStrategy strategy = ResolveFactory.getStrategy(options);

        // then
        assertEquals(TracedResolving.class, strategy.getClass());
    }

    @Test
    public void tracedResolverWhenGlobalTelemetryIsSet() {
        // given
        final FlagdOptions options = FlagdOptions.builder().withGlobalTelemetry(true).build();

        // when
        final ResolveStrategy strategy = ResolveFactory.getStrategy(options);

        // then
        assertEquals(TracedResolving.class, strategy.getClass());
    }
}