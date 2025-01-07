package dev.openfeature.contrib.providers.flagd.resolver.common.nameresolvers;

import io.grpc.Attributes;
import io.grpc.EquivalentAddressGroup;
import io.grpc.NameResolver;
import io.grpc.Status;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Collections;
import java.util.List;

/**
 * Envoy NameResolver, will always override the authority with the specified authority and use the
 * socketAddress to connect.
 *
 * <p>Custom URI Scheme:
 *
 * <p>envoy://[proxy-agent-host]:[proxy-agent-port]/[service-name]
 *
 * <p>`service-name` is used as authority instead host
 */
public class EnvoyResolver extends NameResolver {
    private final URI uri;
    private final String authority;
    private Listener2 listener;

    public EnvoyResolver(URI targetUri) {
        this.uri = targetUri;
        this.authority = targetUri.getPath().substring(1);
    }

    @Override
    public String getServiceAuthority() {
        return authority;
    }

    @Override
    public void shutdown() {}

    @Override
    public void start(Listener2 listener) {
        this.listener = listener;
        this.resolve();
    }

    @Override
    public void refresh() {
        this.resolve();
    }

    private void resolve() {
        try {
            InetSocketAddress address = new InetSocketAddress(this.uri.getHost(), this.uri.getPort());
            Attributes addressGroupAttributes = Attributes.newBuilder()
                    .set(EquivalentAddressGroup.ATTR_AUTHORITY_OVERRIDE, this.authority)
                    .build();
            List<EquivalentAddressGroup> equivalentAddressGroup =
                    Collections.singletonList(new EquivalentAddressGroup(address, addressGroupAttributes));
            ResolutionResult resolutionResult = ResolutionResult.newBuilder()
                    .setAddresses(equivalentAddressGroup)
                    .build();
            this.listener.onResult(resolutionResult);
        } catch (Exception e) {
            this.listener.onError(Status.UNAVAILABLE
                    .withDescription("Unable to resolve host ")
                    .withCause(e));
        }
    }
}
