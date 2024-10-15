package dev.openfeature.contrib.providers.flagd.resolver.common.nameresolvers;

import io.grpc.NameResolver;
import io.grpc.NameResolverProvider;
import java.net.URI;

/**
 * A custom NameResolver provider to resolve gRPC target uri for envoy in the
 * format of.
 *
 * <p>envoy://[proxy-agent-host]:[proxy-agent-port]/[service-name]
 */
public class EnvoyResolverProvider extends NameResolverProvider {
    static final String ENVOY_SCHEME = "envoy";

    @Override
    protected boolean isAvailable() {
        return true;
    }

    // setting priority higher than the default i.e. 5
    // could lead to issue since the resolver override the default
    // dns provider.
    // https://grpc.github.io/grpc-java/javadoc/io/grpc/NameResolverProvider.html?is-external=true#priority()
    @Override
    protected int priority() {
        return 5;
    }

    @Override
    public NameResolver newNameResolver(URI targetUri, NameResolver.Args args) {
        if (!ENVOY_SCHEME.equals(targetUri.getScheme())) {
            return null;
        }

        if (!isValidPath(targetUri.getPath()) || targetUri.getHost() == null || targetUri.getPort() == -1) {
            throw new IllegalArgumentException("Incorrectly formatted target uri; "
                    + "expected: '" + ENVOY_SCHEME + ":[//]<proxy-agent-host>:<proxy-agent-port>/<service-name>';"
                    + "but was '" + targetUri + "'");
        }

        return new EnvoyResolver(targetUri);
    }

    @Override
    public String getDefaultScheme() {
        return ENVOY_SCHEME;
    }

    private static boolean isValidPath(String path) {
        return !path.isEmpty() && !path.substring(1).isEmpty()
                && !path.substring(1).contains("/");
    }
}
