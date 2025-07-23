package dev.openfeature.contrib.testclasses;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

@Getter
public class TestProxySelector extends ProxySelector {
    private final List<URI> selectedUris = new ArrayList<>();

    @Override
    public List<Proxy> select(URI uri) {
        selectedUris.add(uri);
        return List.of(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(uri.getHost(), uri.getPort())));
    }

    @Override
    public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {}
}
