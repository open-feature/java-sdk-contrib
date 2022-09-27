package dev.openfeature.contrib.providers.flagd;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.EnumUtils;

import dev.openfeature.flagd.grpc.Schema.ResolveBooleanRequest;
import dev.openfeature.flagd.grpc.Schema.ResolveBooleanResponse;
import dev.openfeature.flagd.grpc.Schema.ResolveFloatRequest;
import dev.openfeature.flagd.grpc.Schema.ResolveFloatResponse;
import dev.openfeature.flagd.grpc.Schema.ResolveIntRequest;
import dev.openfeature.flagd.grpc.Schema.ResolveIntResponse;
import dev.openfeature.flagd.grpc.Schema.ResolveObjectRequest;
import dev.openfeature.flagd.grpc.Schema.ResolveObjectResponse;
import dev.openfeature.flagd.grpc.Schema.ResolveStringRequest;
import dev.openfeature.flagd.grpc.Schema.ResolveStringResponse;
import dev.openfeature.flagd.grpc.ServiceGrpc;
import dev.openfeature.flagd.grpc.ServiceGrpc.ServiceBlockingStub;
import dev.openfeature.javasdk.EvaluationContext;
import dev.openfeature.javasdk.FeatureProvider;
import dev.openfeature.javasdk.Metadata;
import dev.openfeature.javasdk.ProviderEvaluation;
import dev.openfeature.javasdk.Reason;
import dev.openfeature.javasdk.Structure;
import dev.openfeature.javasdk.Value;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;


/**
 * OpenFeature provider for flagd.
 */
@Slf4j
public class FlagdProvider implements FeatureProvider {

    private ServiceBlockingStub serviceStub;
    static final String PROVIDER_NAME = "flagD Provider";
    static final int DEFAULT_PORT = 8013;
    static final String DEFAULT_HOST = "localhost"; 
    
    /**
     * Create a new FlagdProvider instance.
     *
     * @param protocol transport protocol, "http" or "https"
     * @param host     flagd host, defaults to localhost
     * @param port     flagd port, defaults to 8013
     */
    public FlagdProvider(Protocol protocol, String host, int port) {
        
        this(Protocol.HTTPS == protocol 
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
        this(Protocol.HTTP, DEFAULT_HOST, DEFAULT_PORT);
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
        EvaluationContext ctx) {

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
        EvaluationContext ctx) {
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
        EvaluationContext ctx) {
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
        EvaluationContext ctx) {
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
    public ProviderEvaluation<Value> getObjectEvaluation(String key, Value defaultValue,
        EvaluationContext ctx) {
        ResolveObjectRequest request = ResolveObjectRequest.newBuilder()
            .setFlagKey(key)
            .setContext(this.convertContext(ctx))
            .build();
        ResolveObjectResponse r = this.serviceStub.resolveObject(request);
        return ProviderEvaluation.<Value>builder()
            .value(this.convertObjectResponse(r.getValue()))
            .variant(r.getVariant())
            .reason(this.mapReason(r.getReason()))
            .build();
    }

    // Map FlagD reasons to Java SDK reasons.
    private Reason mapReason(String flagdReason) {
        if (!EnumUtils.isValidEnum(Reason.class, flagdReason)) {
            return Reason.UNKNOWN;
        } else {
            return Reason.valueOf(flagdReason);
        }
    }

    /**
     * Recursively convert protobuf structure to openfeature value.
     */
    private Value convertObjectResponse(com.google.protobuf.Struct protobuf) {
        return this.convertProtobufMap(protobuf.getFieldsMap());
    }

    /**
     * Recursively convert the Evaluation context to a protobuf structure.
     */
    private com.google.protobuf.Struct convertContext(EvaluationContext ctx) {
        return this.convertMap(ctx.asMap()).getStructValue();
    }

    /**
     * Convert any openfeature value to a protobuf value.
     */
    private com.google.protobuf.Value convertAny(Value value) {
        if (value.isList()) {
            return this.convertList(value.asList());
        } else if (value.isStructure()) {
            return this.convertMap(value.asStructure().asMap());
        } else {
            return this.convertPrimitive(value);
        }
    }

    /**
     * Convert any protobuf value to an openfeature value.
     */
    private Value convertAny(com.google.protobuf.Value protobuf) {
        if (protobuf.hasListValue()) {
            return this.convertList(protobuf.getListValue());
        } else if (protobuf.hasStructValue()) {
            return this.convertProtobufMap(protobuf.getStructValue().getFieldsMap());
        } else {
            return this.convertPrimitive(protobuf);
        }
    }

    /**
     * Convert openfeature map to protobuf map.
     */
    private com.google.protobuf.Value convertMap(Map<String, Value> map) {
        Map<String, com.google.protobuf.Value> values = new HashMap<>();

        map.keySet().stream().forEach((String key) -> {
            Value value = map.get(key);
            values.put(key, this.convertAny(value));
        });
        com.google.protobuf.Struct struct = com.google.protobuf.Struct.newBuilder()
            .putAllFields(values).build();
        return com.google.protobuf.Value.newBuilder().setStructValue(struct).build();
    }

    /**
     * Convert protobuf map to openfeature map.
     */
    private Value convertProtobufMap(Map<String, com.google.protobuf.Value> map) {
        Map<String, Value> values = new HashMap<>();

        map.keySet().stream().forEach((String key) -> {
            com.google.protobuf.Value value = map.get(key);
            values.put(key, this.convertAny(value));
        });
        return new Value(new Structure(values));
    }

    /**
     * Convert openfeature list to protobuf list. 
     */
    private com.google.protobuf.Value convertList(List<Value> values) {
        com.google.protobuf.ListValue list = com.google.protobuf.ListValue.newBuilder()
            .addAllValues(values.stream()
            .map(v -> this.convertAny(v)).collect(Collectors.toList())).build();
        return com.google.protobuf.Value.newBuilder().setListValue(list).build();
    }

    /**
     * Convert protobuf list to openfeature list.
     */
    private Value convertList(com.google.protobuf.ListValue protobuf) {
        return new Value(protobuf.getValuesList().stream().map(p -> this.convertAny(p)).collect(Collectors.toList()));
    }

    /**
     * Convert openfeature value to protobuf value.
     */
    private com.google.protobuf.Value convertPrimitive(Value value) {
        com.google.protobuf.Value.Builder builder = com.google.protobuf.Value.newBuilder();

        if (value.isBoolean()) {
            builder.setBoolValue(value.asBoolean());
        } else if (value.isString()) {
            builder.setStringValue(value.asString());
        } else if (value.isNumber()) {
            builder.setNumberValue(value.asDouble());
        } else {
            builder.setNullValue(null);
        }
        return builder.build();
    }

    /**
     * Convert protobuf value openfeature value.
     */
    private Value convertPrimitive(com.google.protobuf.Value protobuf) {
        Value value;
        if (protobuf.hasBoolValue()) {
            value = new Value(protobuf.getBoolValue());
        } else if (protobuf.hasStringValue()) {
            value = new Value(protobuf.getStringValue());
        } else if (protobuf.hasNumberValue()) {
            value = new Value(protobuf.getNumberValue());
        } else {
            value = new Value();
        }
        return value;
    }
}
