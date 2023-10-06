package dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.file;

import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.Connector;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.StreamPayload;

import java.util.concurrent.BlockingQueue;

public class FileConnector implements Connector {

    public FileConnector(final String flagSourcePath){

    }

    @Override public void init() {

    }

    @Override public BlockingQueue<StreamPayload> getStream() {
        return null;
    }

    @Override public void shutdown() throws InterruptedException {
        // NO-OP nothing to do here
    }
}
