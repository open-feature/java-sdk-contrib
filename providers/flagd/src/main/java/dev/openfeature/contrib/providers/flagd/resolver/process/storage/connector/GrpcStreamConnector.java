package dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector;

import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.resolver.common.ChannelBuilder;
import io.grpc.ManagedChannel;
import lombok.extern.java.Log;
import sync.v1.FlagSyncServiceGrpc;
import sync.v1.SyncService;

import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

@Log
public class GrpcStreamConnector implements Connector {
    private static final Random RANDOM = new Random();

    // todo make them instance or through a carrier for testing
    private static final int INIT_BACK_OFF = 2 * 1000;
    private static final int MAX_BACK_OFF = 120 * 1000;

    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private final BlockingQueue<StreamPayload> blockingQueue = new LinkedBlockingQueue<>(5);

    private final ManagedChannel channel;
    private final FlagSyncServiceGrpc.FlagSyncServiceStub serviceStub;

    public GrpcStreamConnector(final FlagdOptions options) {
        channel = ChannelBuilder.nettyChannel(options);
        serviceStub = FlagSyncServiceGrpc.newStub(channel);
    }

    public void init() {
        Thread listener = new Thread(() -> {
            try {
                observeEventStream(blockingQueue, shutdown, serviceStub);
            } catch (InterruptedException e) {
                log.log(Level.WARNING, "Event stream interrupted, flag configurations are stale", e);
            }
        });

        listener.setDaemon(true);
        listener.start();
    }

    @Override
    public BlockingQueue<StreamPayload> getStream() {
        return blockingQueue;
    }


    public void shutdown() {
        shutdown.set(true);
        channel.shutdown();
    }

    // blocking calls, to be used with thread
    static void observeEventStream(final BlockingQueue<StreamPayload> writeTo,
                                   final AtomicBoolean shutdown,
                                   FlagSyncServiceGrpc.FlagSyncServiceStub serviceStub) throws InterruptedException {

        final BlockingQueue<GrpcResponseModel> blockingQueue = new LinkedBlockingQueue<>(5);
        int retryDelay = INIT_BACK_OFF;

        while (!shutdown.get()) {
            final SyncService.SyncFlagsRequest request = SyncService.SyncFlagsRequest.newBuilder().build();
            serviceStub.syncFlags(request, new GrpcStreamHandler(blockingQueue));

            while (!shutdown.get()) {
                GrpcResponseModel response = blockingQueue.take();
                if (response.isComplete()) {
                    break;
                }

                if (response.getError() != null) {
                    log.log(Level.WARNING,
                            String.format("error from grpc connection, retrying in %dms", retryDelay),
                            response.getError());
                    break;
                }

                final SyncService.SyncFlagsResponse flagsResponse = response.getSyncFlagsResponse();
                switch (flagsResponse.getState()) {
                    case SYNC_STATE_ALL:
                        if (!writeTo.offer(
                                new StreamPayload(StreamPayloadType.Data, flagsResponse.getFlagConfiguration()))) {
                            log.log(Level.WARNING, "Stream writing failed");
                        }
                        break;
                    case SYNC_STATE_UNSPECIFIED:
                    case SYNC_STATE_ADD:
                    case SYNC_STATE_UPDATE:
                    case SYNC_STATE_DELETE:
                    case SYNC_STATE_PING:
                    case UNRECOGNIZED:
                        log.info(
                                String.format("Ignored - received payload of state: %s", flagsResponse.getState()));
                }
            }

            writeTo.offer(new StreamPayload(StreamPayloadType.Error, "Error from stream connection, retrying"));

            // busy wait till next attempt
            Thread.sleep(retryDelay + RANDOM.nextInt(INIT_BACK_OFF));

            if (retryDelay < MAX_BACK_OFF) {
                retryDelay = 2 * retryDelay;
            }
        }
    }
}
