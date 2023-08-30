package dev.openfeature.contrib.providers.flagd.resolver.grpc;

/**
 * A converter lambda.
 */
@FunctionalInterface
public interface Convert<OutT extends Object, InT extends Object> {
    OutT convert(InT value);
}