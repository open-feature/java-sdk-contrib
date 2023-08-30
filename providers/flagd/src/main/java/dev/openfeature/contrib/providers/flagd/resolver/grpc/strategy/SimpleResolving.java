package dev.openfeature.contrib.providers.flagd.resolver.grpc.strategy;

import com.google.protobuf.Message;

import java.util.function.Function;

/**
 * {@link SimpleResolving} is a simple request to response resolver.
 */
public class SimpleResolving implements ResolveStrategy {

    @Override
    public <ReqT extends Message, ResT extends Message> ResT resolve(final Function<ReqT, ResT> resolverRef,
                                                                     final Message req, final String key) {
        return resolverRef.apply((ReqT) req);
    }
}
