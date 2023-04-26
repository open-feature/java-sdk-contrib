package dev.openfeature.contrib.providers.flagd;

import com.google.protobuf.Message;

import java.util.function.Function;

/**
 * Request to Response resolving strategy.
 * */
interface ResolveStrategy {
    <ReqT extends Message, ResT extends Message> ResT resolve(final Function<ReqT, ResT> resolverRef, final Message req,
                                                              final String key);
}
