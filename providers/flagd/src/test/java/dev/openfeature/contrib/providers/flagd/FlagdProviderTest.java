package dev.openfeature.contrib.providers.flagd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import dev.openfeature.flagd.grpc.Schema.ResolveBooleanRequest;
import dev.openfeature.flagd.grpc.Schema.ResolveBooleanResponse;
import dev.openfeature.flagd.grpc.Schema.ResolveFloatResponse;
import dev.openfeature.flagd.grpc.Schema.ResolveIntResponse;
import dev.openfeature.flagd.grpc.Schema.ResolveObjectResponse;
import dev.openfeature.flagd.grpc.Schema.ResolveStringResponse;
import dev.openfeature.flagd.grpc.ServiceGrpc;
import dev.openfeature.flagd.grpc.ServiceGrpc.ServiceBlockingStub;
import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.MutableContext;
import dev.openfeature.sdk.MutableStructure;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.Reason;
import dev.openfeature.sdk.Structure;
import dev.openfeature.sdk.Value;
import io.grpc.ManagedChannel;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.unix.DomainSocketAddress;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;

class FlagdProviderTest {

    static final String FLAG_KEY = "some-key";
    static final String BOOL_VARIANT = "on";
    static final String DOUBLE_VARIANT = "half";
    static final String INT_VARIANT = "one-hundred";
    static final String STRING_VARIANT = "greeting";
    static final String OBJECT_VARIANT = "obj";
    static final Reason DEFAULT = Reason.DEFAULT;
    static final Integer INT_VALUE = 100;
    static final Double DOUBLE_VALUE = .5d;
    static final String INNER_STRUCT_KEY = "inner_key";
    static final String INNER_STRUCT_VALUE = "inner_value";
    static final Structure OBJECT_VALUE = new MutableStructure() {
        {
            add(INNER_STRUCT_KEY, INNER_STRUCT_VALUE);
        }
    };
    static final com.google.protobuf.Struct PROTOBUF_STRUCTURE_VALUE = com.google.protobuf.Struct.newBuilder()
            .putFields(INNER_STRUCT_KEY,
                    com.google.protobuf.Value.newBuilder().setStringValue(INNER_STRUCT_VALUE).build())
            .build();
    static final String STRING_VALUE = "hi!";

    static OpenFeatureAPI api;

    @BeforeAll
    public static void init() {
        api = OpenFeatureAPI.getInstance();
    }

    @Test
    void path_arg_should_build_domain_socket_with_correct_path() {
        final String path = "/some/path";

        ServiceBlockingStub mockStub = mock(ServiceBlockingStub.class);
        NettyChannelBuilder mockChannelBuilder = getMockChannelBuilderSocket();

        try (MockedStatic<ServiceGrpc> mockStaticService = mockStatic(ServiceGrpc.class)) {
            mockStaticService.when(() -> ServiceGrpc.newBlockingStub(any(ManagedChannel.class)))
                    .thenReturn(mockStub);

            try (MockedStatic<NettyChannelBuilder> mockStaticChannelBuilder = mockStatic(NettyChannelBuilder.class)) {

                try (MockedConstruction<EpollEventLoopGroup> mockEpollEventLoopGroup = mockConstruction(
                        EpollEventLoopGroup.class,
                        (mock, context) -> {
                        })) {
                    mockStaticChannelBuilder.when(() -> NettyChannelBuilder
                            .forAddress(any(DomainSocketAddress.class))).thenReturn(mockChannelBuilder);
                    new FlagdProvider(path);

                    // verify path matches
                    mockStaticChannelBuilder.verify(() -> NettyChannelBuilder
                            .forAddress(argThat((DomainSocketAddress d) -> {
                                assertEquals(d.path(), path); // path should match
                                return true;
                            })));
                }
            }
        }
    }

    @Test
    void no_args_socket_env_should_build_domain_socket_with_correct_path() throws Exception {
        final String path = "/some/other/path";

        new EnvironmentVariables("FLAGD_SOCKET_PATH", path).execute(() -> {

            ServiceBlockingStub mockStub = mock(ServiceBlockingStub.class);
            NettyChannelBuilder mockChannelBuilder = getMockChannelBuilderSocket();

            try (MockedStatic<ServiceGrpc> mockStaticService = mockStatic(ServiceGrpc.class)) {
                mockStaticService.when(() -> ServiceGrpc.newBlockingStub(any(ManagedChannel.class)))
                        .thenReturn(mockStub);

                try (MockedStatic<NettyChannelBuilder> mockStaticChannelBuilder = mockStatic(
                        NettyChannelBuilder.class)) {

                    try (MockedConstruction<EpollEventLoopGroup> mockEpollEventLoopGroup = mockConstruction(
                            EpollEventLoopGroup.class,
                            (mock, context) -> {
                            })) {
                        mockStaticChannelBuilder.when(() -> NettyChannelBuilder
                                .forAddress(any(DomainSocketAddress.class))).thenReturn(mockChannelBuilder);
                        new FlagdProvider();

                        //verify path matches
                        mockStaticChannelBuilder.verify(() -> NettyChannelBuilder
                                .forAddress(argThat((DomainSocketAddress d) -> {
                                    return d.path() == path;
                                })));
                    }
                }
            }
        });
    }

    @Test
    void host_and_port_arg_should_build_tcp_socket() {
        final String host = "host.com";
        final int port = 1234;

        ServiceBlockingStub mockStub = mock(ServiceBlockingStub.class);
        NettyChannelBuilder mockChannelBuilder = getMockChannelBuilderSocket();

        try (MockedStatic<ServiceGrpc> mockStaticService = mockStatic(ServiceGrpc.class)) {
            mockStaticService.when(() -> ServiceGrpc.newBlockingStub(any(ManagedChannel.class)))
                    .thenReturn(mockStub);

            try (MockedStatic<NettyChannelBuilder> mockStaticChannelBuilder = mockStatic(NettyChannelBuilder.class)) {

                mockStaticChannelBuilder.when(() -> NettyChannelBuilder
                        .forAddress(anyString(), anyInt())).thenReturn(mockChannelBuilder);
                new FlagdProvider(host, port, false, null);

                // verify host/port matches
                mockStaticChannelBuilder.verify(() -> NettyChannelBuilder
                        .forAddress(host, port));
            }
        }
    }

    @Test
    void no_args_host_and_port_env_set_should_build_tcp_socket() throws Exception {
        final String host = "server.com";
        final int port = 4321;

        new EnvironmentVariables("FLAGD_HOST", host, "FLAGD_PORT", String.valueOf(port)).execute(() -> {
            ServiceBlockingStub mockStub = mock(ServiceBlockingStub.class);
            NettyChannelBuilder mockChannelBuilder = getMockChannelBuilderSocket();
    
            try (MockedStatic<ServiceGrpc> mockStaticService = mockStatic(ServiceGrpc.class)) {
                mockStaticService.when(() -> ServiceGrpc.newBlockingStub(any(ManagedChannel.class)))
                        .thenReturn(mockStub);
    
                try (MockedStatic<NettyChannelBuilder> mockStaticChannelBuilder = mockStatic(
                        NettyChannelBuilder.class)) {
    
                    mockStaticChannelBuilder.when(() -> NettyChannelBuilder
                            .forAddress(anyString(), anyInt())).thenReturn(mockChannelBuilder);
                    new FlagdProvider();

                    // verify host/port matches
                    mockStaticChannelBuilder.verify(() -> NettyChannelBuilder
                            .forAddress(host, port));
                }
            }
        });
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

        ResolveObjectResponse objectResponse = ResolveObjectResponse.newBuilder()
                .setValue(PROTOBUF_STRUCTURE_VALUE)
                .setVariant(OBJECT_VARIANT)
                .setReason(DEFAULT.toString())
                .build();

        ServiceBlockingStub serviceBlockingStubMock = mock(ServiceBlockingStub.class);
        when(serviceBlockingStubMock.withDeadlineAfter(anyLong(), any(TimeUnit.class)))
                .thenReturn(serviceBlockingStubMock);
        when(serviceBlockingStubMock
                .resolveBoolean(argThat(x -> FLAG_KEY.equals(x.getFlagKey())))).thenReturn(booleanResponse);
        when(serviceBlockingStubMock
                .resolveFloat(argThat(x -> FLAG_KEY.equals(x.getFlagKey())))).thenReturn(floatResponse);
        when(serviceBlockingStubMock
                .resolveInt(argThat(x -> FLAG_KEY.equals(x.getFlagKey())))).thenReturn(intResponse);
        when(serviceBlockingStubMock
                .resolveString(argThat(x -> FLAG_KEY.equals(x.getFlagKey())))).thenReturn(stringResponse);
        when(serviceBlockingStubMock
                .resolveObject(argThat(x -> FLAG_KEY.equals(x.getFlagKey())))).thenReturn(objectResponse);

        OpenFeatureAPI.getInstance().setProvider(new FlagdProvider(serviceBlockingStubMock));

        FlagEvaluationDetails<Boolean> booleanDetails = api.getClient().getBooleanDetails(FLAG_KEY, false);
        assertTrue(booleanDetails.getValue());
        assertEquals(BOOL_VARIANT, booleanDetails.getVariant());
        assertEquals(DEFAULT.toString(), booleanDetails.getReason());

        FlagEvaluationDetails<String> stringDetails = api.getClient().getStringDetails(FLAG_KEY, "wrong");
        assertEquals(STRING_VALUE, stringDetails.getValue());
        assertEquals(STRING_VARIANT, stringDetails.getVariant());
        assertEquals(DEFAULT.toString(), stringDetails.getReason());

        FlagEvaluationDetails<Integer> intDetails = api.getClient().getIntegerDetails(FLAG_KEY, 0);
        assertEquals(INT_VALUE, intDetails.getValue());
        assertEquals(INT_VARIANT, intDetails.getVariant());
        assertEquals(DEFAULT.toString(), intDetails.getReason());

        FlagEvaluationDetails<Double> floatDetails = api.getClient().getDoubleDetails(FLAG_KEY, 0.1);
        assertEquals(DOUBLE_VALUE, floatDetails.getValue());
        assertEquals(DOUBLE_VARIANT, floatDetails.getVariant());
        assertEquals(DEFAULT.toString(), floatDetails.getReason());

        FlagEvaluationDetails<Value> objectDetails = api.getClient().getObjectDetails(FLAG_KEY, new Value());
        assertEquals(INNER_STRUCT_VALUE, objectDetails.getValue().asStructure()
                .asMap().get(INNER_STRUCT_KEY).asString());
        assertEquals(OBJECT_VARIANT, objectDetails.getVariant());
        assertEquals(DEFAULT.toString(), objectDetails.getReason());
    }

    @Test
    void context_is_parsed_and_passed_to_grpc_service() {

        final String BOOLEAN_ATTR_KEY = "bool-attr";
        final String INT_ATTR_KEY = "int-attr";
        final String STRING_ATTR_KEY = "string-attr";
        final String STRUCT_ATTR_KEY = "struct-attr";
        final String DOUBLE_ATTR_KEY = "double-attr";
        final String LIST_ATTR_KEY = "list-attr";
        final String STRUCT_ATTR_INNER_KEY = "struct-inner-key";

        final Boolean BOOLEAN_ATTR_VALUE = true;
        final Integer INT_ATTR_VALUE = 1;
        final String STRING_ATTR_VALUE = "str";
        final Double DOUBLE_ATTR_VALUE = 0.5d;
        final List<Value> LIST_ATTR_VALUE = new ArrayList<Value>() {
            {
                add(new Value(1));
            }
        };
        final String STRUCT_ATTR_INNER_VALUE = "struct-inner-value";
        final Structure STRUCT_ATTR_VALUE = new MutableStructure().add(STRUCT_ATTR_INNER_KEY, STRUCT_ATTR_INNER_VALUE);
        final String DEFAULT_STRING = "DEFAULT";

        ResolveBooleanResponse booleanResponse = ResolveBooleanResponse.newBuilder()
                .setValue(true)
                .setVariant(BOOL_VARIANT)
                .setReason(DEFAULT_STRING.toString())
                .build();

        ServiceBlockingStub serviceBlockingStubMock = mock(ServiceBlockingStub.class);
        when(serviceBlockingStubMock.withDeadlineAfter(anyLong(), any(TimeUnit.class)))
                .thenReturn(serviceBlockingStubMock);
        when(serviceBlockingStubMock.resolveBoolean(argThat(
                x -> STRING_ATTR_VALUE.equals(x.getContext().getFieldsMap().get(STRING_ATTR_KEY).getStringValue())
                        && INT_ATTR_VALUE == x.getContext().getFieldsMap().get(INT_ATTR_KEY).getNumberValue()
                        && DOUBLE_ATTR_VALUE == x.getContext().getFieldsMap().get(DOUBLE_ATTR_KEY).getNumberValue()
                        && LIST_ATTR_VALUE.get(0).asInteger() == x.getContext().getFieldsMap()
                                .get(LIST_ATTR_KEY).getListValue().getValuesList().get(0).getNumberValue()
                        && x.getContext().getFieldsMap().get(BOOLEAN_ATTR_KEY).getBoolValue()
                        && STRUCT_ATTR_INNER_VALUE.equals(x.getContext().getFieldsMap()
                                .get(STRUCT_ATTR_KEY).getStructValue().getFieldsMap().get(STRUCT_ATTR_INNER_KEY)
                                .getStringValue()))))
                .thenReturn(booleanResponse);

        OpenFeatureAPI.getInstance().setProvider(new FlagdProvider(serviceBlockingStubMock));

        MutableContext context = new MutableContext();
        context.add(BOOLEAN_ATTR_KEY, BOOLEAN_ATTR_VALUE);
        context.add(INT_ATTR_KEY, INT_ATTR_VALUE);
        context.add(DOUBLE_ATTR_KEY, DOUBLE_ATTR_VALUE);
        context.add(LIST_ATTR_KEY, LIST_ATTR_VALUE);
        context.add(STRING_ATTR_KEY, STRING_ATTR_VALUE);
        context.add(STRUCT_ATTR_KEY, STRUCT_ATTR_VALUE);

        FlagEvaluationDetails<Boolean> booleanDetails = api.getClient().getBooleanDetails(FLAG_KEY, false, context);
        assertTrue(booleanDetails.getValue());
        assertEquals(BOOL_VARIANT, booleanDetails.getVariant());
        assertEquals(DEFAULT.toString(), booleanDetails.getReason());
    }

    @Test
    void set_deadline_deadline_send_in_grpc() {
        long deadline = 1300;

        ResolveBooleanResponse badReasonResponse = ResolveBooleanResponse.newBuilder()
                .setValue(true)
                .build();

        ServiceBlockingStub serviceBlockingStubMock = mock(ServiceBlockingStub.class);
        when(serviceBlockingStubMock.withDeadlineAfter(anyLong(), any(TimeUnit.class)))
                        .thenReturn(serviceBlockingStubMock);
        when(serviceBlockingStubMock.resolveBoolean(any(ResolveBooleanRequest.class))).thenReturn(badReasonResponse);

        FlagdProvider provider = new FlagdProvider(serviceBlockingStubMock);
        provider.setDeadline(deadline);
        OpenFeatureAPI.getInstance().setProvider(provider);


        api.getClient().getBooleanDetails(FLAG_KEY, false, new MutableContext());
        verify(serviceBlockingStubMock).withDeadlineAfter(deadline, TimeUnit.MILLISECONDS);
    }

    @Test
    void reason_mapped_correctly_if_unknown() {
        ResolveBooleanResponse badReasonResponse = ResolveBooleanResponse.newBuilder()
                .setValue(true)
                .setVariant(BOOL_VARIANT)
                .setReason("UNKNOWN") // set an invalid reason string
                .build();

        ServiceBlockingStub serviceBlockingStubMock = mock(ServiceBlockingStub.class);
        when(serviceBlockingStubMock.withDeadlineAfter(anyLong(), any(TimeUnit.class)))
                        .thenReturn(serviceBlockingStubMock);
        when(serviceBlockingStubMock.resolveBoolean(any(ResolveBooleanRequest.class))).thenReturn(badReasonResponse);

        OpenFeatureAPI.getInstance().setProvider(new FlagdProvider(serviceBlockingStubMock));

        FlagEvaluationDetails<Boolean> booleanDetails = api.getClient()
                .getBooleanDetails(FLAG_KEY, false, new MutableContext());
        assertEquals(Reason.UNKNOWN.toString(), booleanDetails.getReason()); // reason should be converted to UNKNOWN
    }

    private NettyChannelBuilder getMockChannelBuilderSocket() {
        NettyChannelBuilder mockChannelBuilder = mock(NettyChannelBuilder.class);
        when(mockChannelBuilder.eventLoopGroup(any(EventLoopGroup.class))).thenReturn(mockChannelBuilder);
        when(mockChannelBuilder.channelType(any(Class.class))).thenReturn(mockChannelBuilder);
        when(mockChannelBuilder.usePlaintext()).thenReturn(mockChannelBuilder);
        when(mockChannelBuilder.build()).thenReturn(null);
        return mockChannelBuilder;
    }
}