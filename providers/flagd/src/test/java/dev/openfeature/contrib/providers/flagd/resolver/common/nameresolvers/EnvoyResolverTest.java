package dev.openfeature.contrib.providers.flagd.resolver.common.nameresolvers;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.net.URI;

class EnvoyResolverTest {
    @Test
    void envoyResolverTest() {
        // given
        EnvoyResolver envoyResolver = new EnvoyResolver(URI.create("envoy://localhost:1234/foo.service"));

        // then
        Assertions.assertEquals("foo.service", envoyResolver.getServiceAuthority());
    }
}
