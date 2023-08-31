package dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector;

import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.resolver.common.ChannelBuilder;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import lombok.extern.java.Log;
import sync.v1.FlagSyncServiceGrpc;
import sync.v1.SyncService;

import java.util.function.Consumer;

// handle grpc flag configuration stream
@Log
public class GrpcStreamConnector {

    private final ManagedChannel channel;
    private final FlagSyncServiceGrpc.FlagSyncServiceStub serviceStub;

    public GrpcStreamConnector(final FlagdOptions options) {
        channel = ChannelBuilder.nettyChannel(options);
        serviceStub = FlagSyncServiceGrpc.newStub(channel);
    }

    // todo shutdown

    public void init(final Consumer<String> cb) {
        // todo retry logic ?

        Thread syncHandler = new Thread(() -> this.observeEventStream(cb));
        syncHandler.start();
    }

    private void observeEventStream(final Consumer<String> cb) {
        SyncService.SyncFlagsRequest request = SyncService.SyncFlagsRequest.newBuilder()
                .setSelector("selector")
                .setProviderId("providerID")
                .build();


        this.serviceStub.syncFlags(request, new StreamObserver<SyncService.SyncFlagsResponse>() {
            @Override public void onNext(SyncService.SyncFlagsResponse syncFlagsResponse) {
                switch (syncFlagsResponse.getState()){
                    case SYNC_STATE_ALL:
                        cb.accept(syncFlagsResponse.getFlagConfiguration());
                        break;
                    case SYNC_STATE_UNSPECIFIED:
                    case SYNC_STATE_ADD:
                    case SYNC_STATE_UPDATE:
                    case SYNC_STATE_DELETE:
                    case SYNC_STATE_PING:
                    case UNRECOGNIZED:
                        log.info("Received sate: "+ syncFlagsResponse.getState());
                }

            }

            @Override public void onError(Throwable throwable) {
                // todo handle error
            }

            @Override public void onCompleted() {
                // todo handle complete
            }
        });
    }


}
