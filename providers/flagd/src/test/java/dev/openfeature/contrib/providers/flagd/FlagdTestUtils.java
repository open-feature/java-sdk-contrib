package dev.openfeature.contrib.providers.flagd;

import static org.mockito.Mockito.mock;

import dev.openfeature.contrib.providers.flagd.resolver.Resolver;
import dev.openfeature.contrib.providers.flagd.resolver.common.ChannelConnector;
import dev.openfeature.contrib.providers.flagd.resolver.process.InProcessResolver;
import dev.openfeature.contrib.providers.flagd.resolver.process.MockStorage;
import dev.openfeature.contrib.providers.flagd.resolver.process.model.FeatureFlag;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.StorageState;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.StorageStateChange;
import dev.openfeature.contrib.providers.flagd.resolver.rpc.RpcResolver;
import dev.openfeature.contrib.providers.flagd.resolver.rpc.cache.Cache;
import dev.openfeature.flagd.grpc.evaluation.ServiceGrpc;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

class FlagdTestUtils {
    // test helper
    // create provider with given grpc provider and state supplier
    static FlagdProvider createProvider(ChannelConnector connector, ServiceGrpc.ServiceBlockingStub mockBlockingStub) {
        final Cache cache = new Cache("lru", 5);
        final ServiceGrpc.ServiceStub mockStub = mock(ServiceGrpc.ServiceStub.class);

        return createProvider(connector, cache, mockStub, mockBlockingStub);
    }

    // create provider with given grpc provider, cache and state supplier
    static FlagdProvider createProvider(
            ChannelConnector connector,
            Cache cache,
            ServiceGrpc.ServiceStub mockStub,
            ServiceGrpc.ServiceBlockingStub mockBlockingStub) {
        final FlagdOptions flagdOptions = FlagdOptions.builder().build();
        final RpcResolver grpcResolver = new RpcResolver(flagdOptions, cache, (connectionEvent) -> {});

        try {
            Field resolver = RpcResolver.class.getDeclaredField("connector");
            resolver.setAccessible(true);
            resolver.set(grpcResolver, connector);

            Field stub = RpcResolver.class.getDeclaredField("stub");
            stub.setAccessible(true);
            stub.set(grpcResolver, mockStub);

            Field blockingStub = RpcResolver.class.getDeclaredField("blockingStub");
            blockingStub.setAccessible(true);
            blockingStub.set(grpcResolver, mockBlockingStub);

        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        final FlagdProvider provider = new FlagdProvider(grpcResolver, true);
        return provider;
    }

    static FlagdProvider createInProcessProvider(Map<String, FeatureFlag> mockFlags) {
        final FlagdOptions flagdOptions = FlagdOptions.builder()
                .resolverType(Config.Resolver.IN_PROCESS)
                .offlineFlagSourcePath("") // this is new
                .deadline(1000)
                .build();
        final FlagdProvider provider = new FlagdProvider(flagdOptions);
        final MockStorage mockStorage = new MockStorage(
                mockFlags, new LinkedBlockingQueue<>(Arrays.asList(new StorageStateChange(StorageState.OK))));

        try {
            final Field flagResolver = FlagdProvider.class.getDeclaredField("flagResolver");
            flagResolver.setAccessible(true);
            final Resolver resolver = (Resolver) flagResolver.get(provider);

            final Field flagStore = InProcessResolver.class.getDeclaredField("flagStore");
            flagStore.setAccessible(true);
            flagStore.set(resolver, mockStorage);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        return provider;
    }

    static FlagdProvider createInProcessProvider() {
        return createInProcessProvider(Collections.emptyMap());
    }
}
