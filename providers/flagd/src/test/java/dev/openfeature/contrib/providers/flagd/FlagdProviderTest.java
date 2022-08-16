package dev.openfeature.contrib.providers.flagd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.openfeature.flagd.grpc.Schema.ResolveBooleanResponse;
import dev.openfeature.flagd.grpc.Schema.ResolveFloatResponse;
import dev.openfeature.flagd.grpc.Schema.ResolveIntResponse;
import dev.openfeature.flagd.grpc.Schema.ResolveStringResponse;
import dev.openfeature.flagd.grpc.ServiceGrpc.ServiceBlockingStub;
import dev.openfeature.javasdk.EvaluationContext;
import dev.openfeature.javasdk.FlagEvaluationDetails;
import dev.openfeature.javasdk.OpenFeatureAPI;
import dev.openfeature.javasdk.Reason;

class FlagdProviderTest {

    static final String FLAG_KEY = "some-key";
    static final String BOOL_VARIANT = "on";
    static final String DOUBLE_VARIANT = "half";
    static final String INT_VARIANT = "one-hundred";
    static final String STRING_VARIANT = "greeting";
    static final Reason DEFAULT = Reason.DEFAULT;
    static final Integer INT_VALUE = 100;
    static final Double DOUBLE_VALUE = .5d;
    static final String STRING_VALUE = "hi!";

    static OpenFeatureAPI api;

    @BeforeAll
    public static void init() {
        api = OpenFeatureAPI.getInstance();
    }

    @Test
    void resolvers_call_grpc_service_and_return_details() {
        ResolveBooleanResponse booleanResponse = ResolveBooleanResponse.newBuilder()
            .setValue(true)
            .setVariant(BOOL_VARIANT)
            .setReason(DEFAULT.toString())
            .build();

        ResolveStringResponse stringResponse = ResolveStringResponse.newBuilder()
            .setValue(STRING_VALUE)
            .setVariant(STRING_VARIANT)
            .setReason(DEFAULT.toString())
            .build();

        ResolveIntResponse intResponse = ResolveIntResponse.newBuilder()
            .setValue(INT_VALUE)
            .setVariant(INT_VARIANT)
            .setReason(DEFAULT.toString())
            .build();

        ResolveFloatResponse floatResponse = ResolveFloatResponse.newBuilder()
            .setValue(DOUBLE_VALUE)
            .setVariant(DOUBLE_VARIANT)
            .setReason(DEFAULT.toString())
            .build();

        ServiceBlockingStub serviceBlockingStubMock = mock(ServiceBlockingStub.class);
        when(serviceBlockingStubMock.resolveBoolean(argThat(x -> FLAG_KEY.equals(x.getFlagKey())))).thenReturn(booleanResponse);
        when(serviceBlockingStubMock.resolveFloat(argThat(x -> FLAG_KEY.equals(x.getFlagKey())))).thenReturn(floatResponse);
        when(serviceBlockingStubMock.resolveInt(argThat(x -> FLAG_KEY.equals(x.getFlagKey())))).thenReturn(intResponse);
        when(serviceBlockingStubMock.resolveString(argThat(x -> FLAG_KEY.equals(x.getFlagKey())))).thenReturn(stringResponse);

        OpenFeatureAPI.getInstance().setProvider(new FlagdProvider(serviceBlockingStubMock));
        
        FlagEvaluationDetails<Boolean> booleanDetails = api.getClient().getBooleanDetails(FLAG_KEY, false);
        assertTrue(booleanDetails.getValue());
        assertEquals(BOOL_VARIANT, booleanDetails.getVariant());
        assertEquals(DEFAULT, booleanDetails.getReason());

        FlagEvaluationDetails<String> stringDetails = api.getClient().getStringDetails(FLAG_KEY, "wrong");
        assertEquals(STRING_VALUE, stringDetails.getValue());
        assertEquals(STRING_VARIANT, stringDetails.getVariant());
        assertEquals(DEFAULT, stringDetails.getReason());

        FlagEvaluationDetails<Integer> intDetails = api.getClient().getIntegerDetails(FLAG_KEY, 0);
        assertEquals(INT_VALUE, intDetails.getValue());
        assertEquals(INT_VARIANT, intDetails.getVariant());
        assertEquals(DEFAULT, intDetails.getReason());
        
        FlagEvaluationDetails<Double> floatDetails = api.getClient().getDoubleDetails(FLAG_KEY, 0.1);
        assertEquals(DOUBLE_VALUE, floatDetails.getValue());
        assertEquals(DOUBLE_VARIANT, floatDetails.getVariant());
        assertEquals(DEFAULT, floatDetails.getReason());
    }

    @Test
    void context_is_passed_to_grpc_service() {

        final String BOOLEAN_ATTR_KEY = "bool-attr";
        final String NUM_ATTR_KEY = "int-attr";
        final String STRING_ATTR_KEY = "string-attr";

        final Boolean BOOLEAN_ATTR_VALUE = true;
        final Integer NUM_ATTR_VALUE = 1;
        final String STRING_ATTR_VALUE = "str";

        ResolveBooleanResponse booleanResponse = ResolveBooleanResponse.newBuilder()
            .setValue(true)
            .setVariant(BOOL_VARIANT)
            .setReason(DEFAULT.toString())
            .build();

        ServiceBlockingStub serviceBlockingStubMock = mock(ServiceBlockingStub.class);
        when(serviceBlockingStubMock.resolveBoolean(argThat(x -> 
            STRING_ATTR_VALUE.equals(x.getContext().getFieldsMap().get(STRING_ATTR_KEY).getStringValue()) &&
            NUM_ATTR_VALUE == x.getContext().getFieldsMap().get(NUM_ATTR_KEY).getNumberValue() &&
            x.getContext().getFieldsMap().get(BOOLEAN_ATTR_KEY).getBoolValue()
        ))).thenReturn(booleanResponse);

        OpenFeatureAPI.getInstance().setProvider(new FlagdProvider(serviceBlockingStubMock));
        
        EvaluationContext context = new EvaluationContext();
        context.addBooleanAttribute(BOOLEAN_ATTR_KEY, BOOLEAN_ATTR_VALUE);
        context.addIntegerAttribute(NUM_ATTR_KEY, NUM_ATTR_VALUE);
        context.addStringAttribute(STRING_ATTR_KEY, STRING_ATTR_VALUE);

        FlagEvaluationDetails<Boolean> booleanDetails = api.getClient().getBooleanDetails(FLAG_KEY, false, context);
        assertTrue(booleanDetails.getValue());
        assertEquals(BOOL_VARIANT, booleanDetails.getVariant());
        assertEquals(DEFAULT, booleanDetails.getReason());
    }
}