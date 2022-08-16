package dev.openfeature.contrib.providers.flagd;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.NotImplementedException;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;

import dev.openfeature.javasdk.EvaluationContext;
import dev.openfeature.javasdk.FeatureProvider;
import dev.openfeature.javasdk.FlagEvaluationOptions;
import dev.openfeature.javasdk.Metadata;
import dev.openfeature.javasdk.ProviderEvaluation;
import dev.openfeature.javasdk.Reason;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;
import dev.openfeature.flagd.grpc.Schema.ResolveBooleanRequest;
import dev.openfeature.flagd.grpc.Schema.ResolveBooleanResponse;
import dev.openfeature.flagd.grpc.Schema.ResolveFloatRequest;
import dev.openfeature.flagd.grpc.Schema.ResolveFloatResponse;
import dev.openfeature.flagd.grpc.Schema.ResolveIntRequest;
import dev.openfeature.flagd.grpc.Schema.ResolveIntResponse;
import dev.openfeature.flagd.grpc.Schema.ResolveStringRequest;
import dev.openfeature.flagd.grpc.Schema.ResolveStringResponse;
import dev.openfeature.flagd.grpc.ServiceGrpc;
import dev.openfeature.flagd.grpc.ServiceGrpc.ServiceBlockingStub;

/**
 * OpenFeature provider for flagd.
 */
@Slf4j
public class FlagdProvider implements FeatureProvider {

    private ServiceBlockingStub serviceStub;
    static final String PROVIDER_NAME = "flagD Provider";

    /**
     * Create a new FlagdProvider instance.
     *
     * @param protocol transport protocol, "http" or "https"
     * @param host     flagd host, defaults to localhost
     * @param port     flagd port, defaults to 8013
     */
    public FlagdProvider(String protocol, String host, int port) {
        
        this("https".equalsIgnoreCase(protocol) 
            ? ServiceGrpc.newBlockingStub(ManagedChannelBuilder.forAddress(host, port)
                .useTransportSecurity()
                .build()) :
            ServiceGrpc.newBlockingStub(ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build()));
    }

    /**
     * Create a new FlagdProvider instance.
     */
    public FlagdProvider() {
        this("http", "localhost", 8013);
    }

    /**
     * Create a new FlagdProvider instance.
     *
     * @param serviceStub service stub instance to use
     */
    public FlagdProvider(ServiceBlockingStub serviceStub) {
        this.serviceStub = serviceStub;
    }

    @Override
    public Metadata getMetadata() {
        return new Metadata() {
            @Override
            public String getName() {
                return PROVIDER_NAME;
            }
        };
    }

    @Override
    public ProviderEvaluation<Boolean> getBooleanEvaluation(String key, Boolean defaultValue,
        EvaluationContext ctx, FlagEvaluationOptions options) {

        ResolveBooleanRequest request = ResolveBooleanRequest.newBuilder()
            .setFlagKey(key)
            .setContext(this.convertContext(ctx))
            .build();
        ResolveBooleanResponse r = this.serviceStub.resolveBoolean(request);
        return ProviderEvaluation.<Boolean>builder()
            .value(r.getValue())
            .variant(r.getVariant())
            .reason(this.mapReason(r.getReason()))
            .build();
    }

    @Override
    public ProviderEvaluation<String> getStringEvaluation(String key, String defaultValue,
        EvaluationContext ctx, FlagEvaluationOptions options) {
        ResolveStringRequest request = ResolveStringRequest.newBuilder()
            .setFlagKey(key)
            .setContext(this.convertContext(ctx)).build();
        ResolveStringResponse r = this.serviceStub.resolveString(request);
        return ProviderEvaluation.<String>builder().value(r.getValue())
            .variant(r.getVariant())
            .reason(this.mapReason(r.getReason()))
            .build();
    }

    @Override
    public ProviderEvaluation<Double> getDoubleEvaluation(String key, Double defaultValue,
        EvaluationContext ctx, FlagEvaluationOptions options) {
        ResolveFloatRequest request = ResolveFloatRequest.newBuilder()
            .setFlagKey(key)
            .setContext(this.convertContext(ctx))
            .build();
        ResolveFloatResponse r = this.serviceStub.resolveFloat(request);
        return ProviderEvaluation.<Double>builder()
            .value(r.getValue())
            .variant(r.getVariant())
            .reason(this.mapReason(r.getReason()))
            .build();
    }

    @Override
    public ProviderEvaluation<Integer> getIntegerEvaluation(String key, Integer defaultValue,
        EvaluationContext ctx, FlagEvaluationOptions options) {
        ResolveIntRequest request = ResolveIntRequest.newBuilder()
            .setFlagKey(key)
            .setContext(this.convertContext(ctx))
            .build();
        ResolveIntResponse r = this.serviceStub.resolveInt(request);
        return ProviderEvaluation.<Integer>builder()
            .value((int) r.getValue())
            .variant(r.getVariant())
            .reason(this.mapReason(r.getReason()))
            .build();
    }

    @Override
    public <T> ProviderEvaluation<T> getObjectEvaluation(String key, T defaultValue,
        EvaluationContext ctx, FlagEvaluationOptions options) {
        throw new NotImplementedException();
    }

    // Map FlagD reasons to Java SDK reasons.
    private Reason mapReason(String flagDreason) {
        if (!EnumUtils.isValidEnum(Reason.class, flagDreason)) {
            // until we have "STATIC" in the spec and SDK, we map STATIC to DEFAULT
            if ("STATIC".equals(flagDreason)) {
                return Reason.DEFAULT;
            } else {
                return Reason.UNKNOWN;
            }
        } else {
            return Reason.valueOf(flagDreason);
        }
    }

    private Struct convertContext(EvaluationContext ctx) {
        // TODO: structure attributes?
        Map<String, Boolean> booleanAttributes = ctx.getBooleanAttributes();
        Map<String, String> stringAttributes = ctx.getStringAttributes();
        Map<String, Integer> intAttributes = ctx.getIntegerAttributes();
        Map<String, Value> values = new HashMap<>();
        booleanAttributes.keySet().stream().forEach((String key) -> values.put(key, Value
            .newBuilder()
            .setBoolValue(booleanAttributes.get(key))
            .build()));
        stringAttributes.keySet().stream().forEach((String key) -> values.put(key,
            Value.newBuilder().setStringValue(stringAttributes
                .get(key))
                .build()));
        intAttributes.keySet().stream().forEach((String key) -> values.put(key, Value
            .newBuilder().setNumberValue(intAttributes.get(key))
            .build()));
        return Struct.newBuilder()
            .putAllFields(values)
            .build();
    }

}
