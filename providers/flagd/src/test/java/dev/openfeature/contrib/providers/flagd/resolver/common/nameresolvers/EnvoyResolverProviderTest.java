package dev.openfeature.contrib.providers.flagd.resolver.common.nameresolvers;

import java.net.URI;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class EnvoyResolverProviderTest {
    private final EnvoyResolverProvider provider = new EnvoyResolverProvider();

    @Test
    void envoyProviderTestScheme() {
        Assertions.assertTrue(provider.isAvailable());
        Assertions.assertNotNull(provider.newNameResolver(URI.create("envoy://localhost:1234/foo.service"), null));
        Assertions.assertNull(
                provider.newNameResolver(URI.create("invalid-scheme://localhost:1234/foo.service"), null));
    }

    @ParameterizedTest
    @MethodSource("getInvalidPaths")
    void invalidTargetUriTests(String mockUri) {
        Exception exception = Assertions.assertThrows(IllegalArgumentException.class, () -> {
            provider.newNameResolver(URI.create(mockUri), null);
        });

        Assertions.assertTrue(exception.toString().contains("Incorrectly formatted target uri"));
    }

    private static Stream<Arguments> getInvalidPaths() {
        return Stream.of(
                Arguments.of("envoy://localhost:1234/test.service/test"),
                Arguments.of("envoy://localhost:1234/"),
                Arguments.of("envoy://localhost:1234"),
                Arguments.of("envoy://localhost/test.service"),
                Arguments.of("envoy:///test.service"));
    }
}
