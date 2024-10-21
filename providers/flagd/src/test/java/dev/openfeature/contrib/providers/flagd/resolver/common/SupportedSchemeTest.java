package dev.openfeature.contrib.providers.flagd.resolver.common;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SupportedSchemeTest {
    @Test
    void supportedSchemeEnumTest() {
        Assertions.assertEquals("envoy", SupportedScheme.ENVOY.getScheme());
        Assertions.assertEquals("dns", SupportedScheme.DNS.getScheme());
        Assertions.assertEquals("xds", SupportedScheme.XDS.getScheme());
        Assertions.assertEquals("uds", SupportedScheme.UDS.getScheme());
    }
}
