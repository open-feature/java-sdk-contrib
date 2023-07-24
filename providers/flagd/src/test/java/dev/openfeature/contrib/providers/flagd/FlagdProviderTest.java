package dev.openfeature.contrib.providers.flagd;

import com.google.protobuf.Struct;
import dev.openfeature.flagd.grpc.Schema.EventStreamRequest;
import dev.openfeature.flagd.grpc.Schema.EventStreamResponse;
import dev.openfeature.flagd.grpc.Schema.ResolveBooleanRequest;
import dev.openfeature.flagd.grpc.Schema.ResolveBooleanResponse;
import dev.openfeature.flagd.grpc.Schema.ResolveFloatResponse;
import dev.openfeature.flagd.grpc.Schema.ResolveIntResponse;
import dev.openfeature.flagd.grpc.Schema.ResolveObjectResponse;
import dev.openfeature.flagd.grpc.Schema.ResolveStringResponse;
import dev.openfeature.flagd.grpc.ServiceGrpc;
import dev.openfeature.flagd.grpc.ServiceGrpc.ServiceBlockingStub;
import dev.openfeature.flagd.grpc.ServiceGrpc.ServiceStub;
import dev.openfeature.sdk.*;
import io.grpc.Channel;
import io.grpc.Deadline;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.unix.DomainSocketAddress;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static dev.openfeature.contrib.providers.flagd.Config.CACHED_REASON;
import static dev.openfeature.contrib.providers.flagd.Config.STATIC_REASON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlagdProviderTest {

    static final String FLAG_KEY = "some-key";
    static final String FLAG_KEY_BOOLEAN = "some-key-boolean";
    static final String FLAG_KEY_INTEGER = "some-key-integer";
    static final String FLAG_KEY_DOUBLE = "some-key-double";
    static final String FLAG_KEY_STRING = "some-key-string";
    static final String FLAG_KEY_OBJECT = "some-key-object";
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

        ServiceBlockingStub mockBlockingStub = mock(ServiceBlockingStub.class);
        ServiceStub mockStub = mock(ServiceStub.class);
        NettyChannelBuilder mockChannelBuilder = getMockChannelBuilderSocket();

        try (MockedStatic<ServiceGrpc> mockStaticService = mockStatic(ServiceGrpc.class)) {
            mockStaticService.when(() -> ServiceGrpc.newBlockingStub(any(Channel.class)))
                    .thenReturn(mockBlockingStub);
            mockStaticService.when(() -> ServiceGrpc.newStub(any()))
                    .thenReturn(mockStub);

            try (MockedStatic<NettyChannelBuilder> mockStaticChannelBuilder = mockStatic(NettyChannelBuilder.class)) {

                try (MockedConstruction<EpollEventLoopGroup> mockEpollEventLoopGroup = mockConstruction(
                        EpollEventLoopGroup.class,
                        (mock, context) -> {
                        })) {
                    when(NettyChannelBuilder.forAddress(any(DomainSocketAddress.class))).thenReturn(mockChannelBuilder);

                    new FlagdProvider(FlagdOptions.builder().socketPath(path).build());

                    // verify path matches
                    mockStaticChannelBuilder.verify(() -> NettyChannelBuilder
                            .forAddress(argThat((DomainSocketAddress d) -> {
                                assertEquals(d.path(), path); // path should match
                                return true;
                            })), times(1));
                }
            }
        }
    }

    @Test
    void no_args_socket_env_should_build_domain_socket_with_correct_path() throws Exception {
        final String path = "/some/other/path";

        new EnvironmentVariables("FLAGD_SOCKET_PATH", path).execute(() -> {

            ServiceBlockingStub mockBlockingStub = mock(ServiceBlockingStub.class);
            ServiceStub mockStub = mock(ServiceStub.class);
            NettyChannelBuilder mockChannelBuilder = getMockChannelBuilderSocket();

            try (MockedStatic<ServiceGrpc> mockStaticService = mockStatic(ServiceGrpc.class)) {
                mockStaticService.when(() -> ServiceGrpc.newBlockingStub(any(Channel.class)))
                        .thenReturn(mockBlockingStub);
                mockStaticService.when(() -> ServiceGrpc.newStub(any()))
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

                        //verify path matches & called times(= 1 as we rely on reusable channel)
                        mockStaticChannelBuilder.verify(() -> NettyChannelBuilder
                                .forAddress(argThat((DomainSocketAddress d) -> {
                                    return d.path() == path;
                                })), times(1));
                    }
                }
            }
        });
    }

    @Test
    void host_and_port_arg_should_build_tcp_socket() {
        final String host = "host.com";
        final int port = 1234;

        ServiceBlockingStub mockBlockingStub = mock(ServiceBlockingStub.class);
        ServiceStub mockStub = mock(ServiceStub.class);
        NettyChannelBuilder mockChannelBuilder = getMockChannelBuilderSocket();

        try (MockedStatic<ServiceGrpc> mockStaticService = mockStatic(ServiceGrpc.class)) {
            mockStaticService.when(() -> ServiceGrpc.newBlockingStub(any(Channel.class)))
                    .thenReturn(mockBlockingStub);
            mockStaticService.when(() -> ServiceGrpc.newStub(any()))
                    .thenReturn(mockStub);

            try (MockedStatic<NettyChannelBuilder> mockStaticChannelBuilder = mockStatic(NettyChannelBuilder.class)) {

                mockStaticChannelBuilder.when(() -> NettyChannelBuilder
                        .forAddress(anyString(), anyInt())).thenReturn(mockChannelBuilder);

                final FlagdOptions flagdOptions = FlagdOptions.builder().host(host).port(port).tls(false).build();
                new FlagdProvider(flagdOptions);

                // verify host/port matches
                mockStaticChannelBuilder.verify(() -> NettyChannelBuilder
                        .forAddress(host, port), times(1));
            }
        }
    }

    @Test
    void no_args_host_and_port_env_set_should_build_tcp_socket() throws Exception {
        final String host = "server.com";
        final int port = 4321;

        new EnvironmentVariables("FLAGD_HOST", host, "FLAGD_PORT", String.valueOf(port)).execute(() -> {
            ServiceBlockingStub mockBlockingStub = mock(ServiceBlockingStub.class);
            ServiceStub mockStub = mock(ServiceStub.class);
            NettyChannelBuilder mockChannelBuilder = getMockChannelBuilderSocket();
    
            try (MockedStatic<ServiceGrpc> mockStaticService = mockStatic(ServiceGrpc.class)) {
                mockStaticService.when(() -> ServiceGrpc.newBlockingStub(any(Channel.class)))
                        .thenReturn(mockBlockingStub);
                mockStaticService.when(() -> ServiceGrpc.newStub(any()))
                        .thenReturn(mockStub);

                try (MockedStatic<NettyChannelBuilder> mockStaticChannelBuilder = mockStatic(
                        NettyChannelBuilder.class)) {

                    mockStaticChannelBuilder.when(() -> NettyChannelBuilder
                            .forAddress(anyString(), anyInt())).thenReturn(mockChannelBuilder);

                    new FlagdProvider();

                    // verify host/port matches & called times(= 1 as we rely on reusable channel)
                    mockStaticChannelBuilder.verify(() -> NettyChannelBuilder.
                            forAddress(host, port), times(1));
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
        ServiceStub serviceStubMock = mock(ServiceStub.class);
        when(serviceBlockingStubMock.withDeadlineAfter(anyLong(), any(TimeUnit.class)))
                .thenReturn(serviceBlockingStubMock);
        when(serviceBlockingStubMock
                .resolveBoolean(argThat(x -> FLAG_KEY_BOOLEAN.equals(x.getFlagKey())))).thenReturn(booleanResponse);
        when(serviceBlockingStubMock
                .resolveFloat(argThat(x -> FLAG_KEY_DOUBLE.equals(x.getFlagKey())))).thenReturn(floatResponse);
        when(serviceBlockingStubMock
                .resolveInt(argThat(x -> FLAG_KEY_INTEGER.equals(x.getFlagKey())))).thenReturn(intResponse);
        when(serviceBlockingStubMock
                .resolveString(argThat(x -> FLAG_KEY_STRING.equals(x.getFlagKey())))).thenReturn(stringResponse);
        when(serviceBlockingStubMock
                .resolveObject(argThat(x -> FLAG_KEY_OBJECT.equals(x.getFlagKey())))).thenReturn(objectResponse);

        OpenFeatureAPI.getInstance().setProvider(new FlagdProvider(serviceBlockingStubMock, serviceStubMock, "lru",
            100, 5 ));

        FlagEvaluationDetails<Boolean> booleanDetails = api.getClient().getBooleanDetails(FLAG_KEY_BOOLEAN, false);
        assertTrue(booleanDetails.getValue());
        assertEquals(BOOL_VARIANT, booleanDetails.getVariant());
        assertEquals(DEFAULT.toString(), booleanDetails.getReason());

        FlagEvaluationDetails<String> stringDetails = api.getClient().getStringDetails(FLAG_KEY_STRING, "wrong");
        assertEquals(STRING_VALUE, stringDetails.getValue());
        assertEquals(STRING_VARIANT, stringDetails.getVariant());
        assertEquals(DEFAULT.toString(), stringDetails.getReason());

        FlagEvaluationDetails<Integer> intDetails = api.getClient().getIntegerDetails(FLAG_KEY_INTEGER, 0);
        assertEquals(INT_VALUE, intDetails.getValue());
        assertEquals(INT_VARIANT, intDetails.getVariant());
        assertEquals(DEFAULT.toString(), intDetails.getReason());

        FlagEvaluationDetails<Double> floatDetails = api.getClient().getDoubleDetails(FLAG_KEY_DOUBLE, 0.1);
        assertEquals(DOUBLE_VALUE, floatDetails.getValue());
        assertEquals(DOUBLE_VARIANT, floatDetails.getVariant());
        assertEquals(DEFAULT.toString(), floatDetails.getReason());

        FlagEvaluationDetails<Value> objectDetails = api.getClient().getObjectDetails(FLAG_KEY_OBJECT, new Value());
        assertEquals(INNER_STRUCT_VALUE, objectDetails.getValue().asStructure()
                .asMap().get(INNER_STRUCT_KEY).asString());
        assertEquals(OBJECT_VARIANT, objectDetails.getVariant());
        assertEquals(DEFAULT.toString(), objectDetails.getReason());
    }

    @Test
    void zero_value() {
        ResolveBooleanResponse booleanResponse = ResolveBooleanResponse.newBuilder()
                .setVariant(BOOL_VARIANT)
                .setReason(DEFAULT.toString())
                .build();

        ResolveStringResponse stringResponse = ResolveStringResponse.newBuilder()
                .setVariant(STRING_VARIANT)
                .setReason(DEFAULT.toString())
                .build();

        ResolveIntResponse intResponse = ResolveIntResponse.newBuilder()
                .setVariant(INT_VARIANT)
                .setReason(DEFAULT.toString())
                .build();

        ResolveFloatResponse floatResponse = ResolveFloatResponse.newBuilder()
                .setVariant(DOUBLE_VARIANT)
                .setReason(DEFAULT.toString())
                .build();

        ResolveObjectResponse objectResponse = ResolveObjectResponse.newBuilder()
                .setVariant(OBJECT_VARIANT)
                .setReason(DEFAULT.toString())
                .build();

        ServiceBlockingStub serviceBlockingStubMock = mock(ServiceBlockingStub.class);
        ServiceStub serviceStubMock = mock(ServiceStub.class);
        when(serviceBlockingStubMock.withDeadlineAfter(anyLong(), any(TimeUnit.class)))
                .thenReturn(serviceBlockingStubMock);
        when(serviceBlockingStubMock
                .resolveBoolean(argThat(x -> FLAG_KEY_BOOLEAN.equals(x.getFlagKey())))).thenReturn(booleanResponse);
        when(serviceBlockingStubMock
                .resolveFloat(argThat(x -> FLAG_KEY_DOUBLE.equals(x.getFlagKey())))).thenReturn(floatResponse);
        when(serviceBlockingStubMock
                .resolveInt(argThat(x -> FLAG_KEY_INTEGER.equals(x.getFlagKey())))).thenReturn(intResponse);
        when(serviceBlockingStubMock
                .resolveString(argThat(x -> FLAG_KEY_STRING.equals(x.getFlagKey())))).thenReturn(stringResponse);
        when(serviceBlockingStubMock
                .resolveObject(argThat(x -> FLAG_KEY_OBJECT.equals(x.getFlagKey())))).thenReturn(objectResponse);

        OpenFeatureAPI.getInstance().setProvider(new FlagdProvider(serviceBlockingStubMock, serviceStubMock, "lru",
            100, 5 ));

        FlagEvaluationDetails<Boolean> booleanDetails = api.getClient().getBooleanDetails(FLAG_KEY_BOOLEAN, false);
        assertEquals(false, booleanDetails.getValue());
        assertEquals(BOOL_VARIANT, booleanDetails.getVariant());
        assertEquals(DEFAULT.toString(), booleanDetails.getReason());

        FlagEvaluationDetails<String> stringDetails = api.getClient().getStringDetails(FLAG_KEY_STRING, "wrong");
        assertEquals("", stringDetails.getValue());
        assertEquals(STRING_VARIANT, stringDetails.getVariant());
        assertEquals(DEFAULT.toString(), stringDetails.getReason());

        FlagEvaluationDetails<Integer> intDetails = api.getClient().getIntegerDetails(FLAG_KEY_INTEGER, 0);
        assertEquals(0, intDetails.getValue());
        assertEquals(INT_VARIANT, intDetails.getVariant());
        assertEquals(DEFAULT.toString(), intDetails.getReason());

        FlagEvaluationDetails<Double> floatDetails = api.getClient().getDoubleDetails(FLAG_KEY_DOUBLE, 0.1);
        assertEquals(0.0, floatDetails.getValue());
        assertEquals(DOUBLE_VARIANT, floatDetails.getVariant());
        assertEquals(DEFAULT.toString(), floatDetails.getReason());

        FlagEvaluationDetails<Value> objectDetails = api.getClient().getObjectDetails(FLAG_KEY_OBJECT, new Value());
        assertEquals(new MutableStructure(), objectDetails.getValue().asObject());
        assertEquals(OBJECT_VARIANT, objectDetails.getVariant());
        assertEquals(DEFAULT.toString(), objectDetails.getReason());
    }

    @Test
    void resolvers_cache_responses_if_static_and_event_stream_alive() {
        do_resolvers_cache_responses(STATIC_REASON, ProviderState.READY, true);
    }

    @Test
    void resolvers_should_not_cache_responses_if_not_static() {
        do_resolvers_cache_responses(DEFAULT.toString(), ProviderState.READY, false);
    }

    @Test
    void resolvers_should_not_cache_responses_if_event_stream_not_alive() {
        do_resolvers_cache_responses(STATIC_REASON, ProviderState.ERROR, false);
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
        ServiceStub serviceStubMock = mock(ServiceStub.class);
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

        OpenFeatureAPI.getInstance().setProvider(new FlagdProvider(serviceBlockingStubMock, serviceStubMock, "lru",
        100, 5));

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
    // Validates null handling - https://github.com/open-feature/java-sdk-contrib/issues/258
    void null_context_handling(){
        // given
        final String flagA = "flagA";
        final boolean defaultVariant = false;
        final boolean expectedVariant = true;

        final MutableContext context = new MutableContext();
        context.add("key", (String) null);

        final ServiceBlockingStub serviceBlockingStubMock = mock(ServiceBlockingStub.class);
        final ServiceStub serviceStubMock = mock(ServiceStub.class);

        // when
        when(serviceBlockingStubMock.withDeadlineAfter(anyLong(), any(TimeUnit.class)))
                .thenReturn(serviceBlockingStubMock);
        when(serviceBlockingStubMock.resolveBoolean(any()))
                .thenReturn(ResolveBooleanResponse.newBuilder().setValue(expectedVariant).build());

        OpenFeatureAPI.getInstance()
                .setProvider(new FlagdProvider(serviceBlockingStubMock, serviceStubMock, "lru", 10, 1));

        // then
        final Boolean evaluation = api.getClient().getBooleanValue(flagA, defaultVariant, context);

        assertNotEquals(evaluation, defaultVariant);
        assertEquals(evaluation, expectedVariant);
    }

    @Test
    void set_deadline_deadline_send_in_grpc() {
        long deadline = 1300;

        ResolveBooleanResponse badReasonResponse = ResolveBooleanResponse.newBuilder()
                .setValue(true)
                .build();

        ServiceBlockingStub serviceBlockingStubMock = mock(ServiceBlockingStub.class);
        ServiceStub serviceStubMock = mock(ServiceStub.class);
        when(serviceBlockingStubMock.withDeadlineAfter(anyLong(), any(TimeUnit.class)))
                        .thenReturn(serviceBlockingStubMock);
        when(serviceBlockingStubMock.resolveBoolean(any(ResolveBooleanRequest.class))).thenReturn(badReasonResponse);

        FlagdProvider provider = new FlagdProvider(serviceBlockingStubMock, serviceStubMock, "lru", 100, 5);
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
        ServiceStub serviceStubMock = mock(ServiceStub.class);
        when(serviceBlockingStubMock.withDeadlineAfter(anyLong(), any(TimeUnit.class)))
                        .thenReturn(serviceBlockingStubMock);
        when(serviceBlockingStubMock.resolveBoolean(any(ResolveBooleanRequest.class))).thenReturn(badReasonResponse);

        OpenFeatureAPI.getInstance().setProvider(new FlagdProvider(serviceBlockingStubMock, serviceStubMock, "lru", 100, 5));

        FlagEvaluationDetails<Boolean> booleanDetails = api.getClient()
                .getBooleanDetails(FLAG_KEY, false, new MutableContext());
        assertEquals(Reason.UNKNOWN.toString(), booleanDetails.getReason()); // reason should be converted to UNKNOWN
    }


    @Test
    void invalidate_cache() {
        ResolveBooleanResponse booleanResponse = ResolveBooleanResponse.newBuilder()
                .setValue(true)
                .setVariant(BOOL_VARIANT)
                .setReason(STATIC_REASON)
                .build();

        ResolveStringResponse stringResponse = ResolveStringResponse.newBuilder()
                .setValue(STRING_VALUE)
                .setVariant(STRING_VARIANT)
                .setReason(STATIC_REASON)
                .build();

        ResolveIntResponse intResponse = ResolveIntResponse.newBuilder()
                .setValue(INT_VALUE)
                .setVariant(INT_VARIANT)
                .setReason(STATIC_REASON)
                .build();

        ResolveFloatResponse floatResponse = ResolveFloatResponse.newBuilder()
                .setValue(DOUBLE_VALUE)
                .setVariant(DOUBLE_VARIANT)
                .setReason(STATIC_REASON)
                .build();

        ResolveObjectResponse objectResponse = ResolveObjectResponse.newBuilder()
                .setValue(PROTOBUF_STRUCTURE_VALUE)
                .setVariant(OBJECT_VARIANT)
                .setReason(STATIC_REASON)
                .build();


        ServiceBlockingStub serviceBlockingStubMock = mock(ServiceBlockingStub.class);
        ServiceStub serviceStubMock = mock(ServiceStub.class);
        when(serviceStubMock.withWaitForReady()).thenReturn(serviceStubMock);
        when(serviceStubMock.withDeadline(any(Deadline.class)))
                .thenReturn(serviceStubMock);
        when(serviceBlockingStubMock.withWaitForReady()).thenReturn(serviceBlockingStubMock);
        when(serviceBlockingStubMock.withDeadline(any(Deadline.class)))
                .thenReturn(serviceBlockingStubMock);
        when(serviceBlockingStubMock.withDeadlineAfter(anyLong(), any(TimeUnit.class)))
                .thenReturn(serviceBlockingStubMock);
        when(serviceBlockingStubMock
                .resolveBoolean(argThat(x -> FLAG_KEY_BOOLEAN.equals(x.getFlagKey())))).thenReturn(booleanResponse);
        when(serviceBlockingStubMock
                .resolveFloat(argThat(x -> FLAG_KEY_DOUBLE.equals(x.getFlagKey())))).thenReturn(floatResponse);
        when(serviceBlockingStubMock
                .resolveInt(argThat(x -> FLAG_KEY_INTEGER.equals(x.getFlagKey())))).thenReturn(intResponse);
        when(serviceBlockingStubMock
                .resolveString(argThat(x -> FLAG_KEY_STRING.equals(x.getFlagKey())))).thenReturn(stringResponse);
        when(serviceBlockingStubMock
                .resolveObject(argThat(x -> FLAG_KEY_OBJECT.equals(x.getFlagKey())))).thenReturn(objectResponse);

        FlagdProvider provider = new FlagdProvider(serviceBlockingStubMock, serviceStubMock, "lru", 100, 5);
        provider.initialize(null);
        ArgumentCaptor<StreamObserver<EventStreamResponse>> streamObserverCaptor = ArgumentCaptor.forClass(StreamObserver.class);
        verify(serviceStubMock).eventStream(any(EventStreamRequest.class), streamObserverCaptor.capture());

        provider.setState(ProviderState.READY);
        OpenFeatureAPI.getInstance().setProvider(provider);

        HashMap<String, com.google.protobuf.Value> flagsMap = new HashMap<String, com.google.protobuf.Value>();
        HashMap<String, com.google.protobuf.Value> structMap = new HashMap<String, com.google.protobuf.Value>();

        flagsMap.put(FLAG_KEY_BOOLEAN, com.google.protobuf.Value.newBuilder().setStringValue("foo").build());
        flagsMap.put(FLAG_KEY_STRING, com.google.protobuf.Value.newBuilder().setStringValue("foo").build());
        flagsMap.put(FLAG_KEY_INTEGER, com.google.protobuf.Value.newBuilder().setStringValue("foo").build());
        flagsMap.put(FLAG_KEY_DOUBLE, com.google.protobuf.Value.newBuilder().setStringValue("foo").build());
        flagsMap.put(FLAG_KEY_OBJECT, com.google.protobuf.Value.newBuilder().setStringValue("foo").build());

        structMap.put("flags", com.google.protobuf.Value.newBuilder().
                setStructValue(Struct.newBuilder().putAllFields(flagsMap)).build());

        EventStreamResponse eResponse = EventStreamResponse.newBuilder()
                .setType("configuration_change")
                .setData(Struct.newBuilder().putAllFields(structMap).build())
                .build();
        
        // should cache results
        FlagEvaluationDetails<Boolean> booleanDetails = api.getClient().getBooleanDetails(FLAG_KEY_BOOLEAN, false);
        FlagEvaluationDetails<String> stringDetails = api.getClient().getStringDetails(FLAG_KEY_STRING, "wrong");
        FlagEvaluationDetails<Integer> intDetails = api.getClient().getIntegerDetails(FLAG_KEY_INTEGER, 0);
        FlagEvaluationDetails<Double> floatDetails = api.getClient().getDoubleDetails(FLAG_KEY_DOUBLE, 0.1);
        FlagEvaluationDetails<Value> objectDetails = api.getClient().getObjectDetails(FLAG_KEY_OBJECT, new Value());

        // should clear cache
        streamObserverCaptor.getValue().onNext(eResponse);

        // assert cache has been invalidated
        booleanDetails = api.getClient().getBooleanDetails(FLAG_KEY_BOOLEAN, false);
        assertTrue(booleanDetails.getValue());
        assertEquals(BOOL_VARIANT, booleanDetails.getVariant());
        assertEquals(STATIC_REASON, booleanDetails.getReason());

        stringDetails = api.getClient().getStringDetails(FLAG_KEY_STRING, "wrong");
        assertEquals(STRING_VALUE, stringDetails.getValue());
        assertEquals(STRING_VARIANT, stringDetails.getVariant());
        assertEquals(STATIC_REASON, stringDetails.getReason());

        intDetails = api.getClient().getIntegerDetails(FLAG_KEY_INTEGER, 0);
        assertEquals(INT_VALUE, intDetails.getValue());
        assertEquals(INT_VARIANT, intDetails.getVariant());
        assertEquals(STATIC_REASON, intDetails.getReason());

        floatDetails = api.getClient().getDoubleDetails(FLAG_KEY_DOUBLE, 0.1);
        assertEquals(DOUBLE_VALUE, floatDetails.getValue());
        assertEquals(DOUBLE_VARIANT, floatDetails.getVariant());
        assertEquals(STATIC_REASON, floatDetails.getReason());

        objectDetails = api.getClient().getObjectDetails(FLAG_KEY_OBJECT, new Value());
        assertEquals(INNER_STRUCT_VALUE, objectDetails.getValue().asStructure()
                .asMap().get(INNER_STRUCT_KEY).asString());
        assertEquals(OBJECT_VARIANT, objectDetails.getVariant());
        assertEquals(STATIC_REASON, objectDetails.getReason());
    }

    private NettyChannelBuilder getMockChannelBuilderSocket() {
        NettyChannelBuilder mockChannelBuilder = mock(NettyChannelBuilder.class);
        when(mockChannelBuilder.eventLoopGroup(any(EventLoopGroup.class))).thenReturn(mockChannelBuilder);
        when(mockChannelBuilder.channelType(any(Class.class))).thenReturn(mockChannelBuilder);
        when(mockChannelBuilder.usePlaintext()).thenReturn(mockChannelBuilder);
        when(mockChannelBuilder.build()).thenReturn(null);
        return mockChannelBuilder;
    }

    private void do_resolvers_cache_responses(String reason, ProviderState eventStreamAlive, Boolean shouldCache) {
        String expectedReason = CACHED_REASON;
        if (!shouldCache) {
            expectedReason = reason;
        }

        ResolveBooleanResponse booleanResponse = ResolveBooleanResponse.newBuilder()
                .setValue(true)
                .setVariant(BOOL_VARIANT)
                .setReason(reason)
                .build();

        ResolveStringResponse stringResponse = ResolveStringResponse.newBuilder()
                .setValue(STRING_VALUE)
                .setVariant(STRING_VARIANT)
                .setReason(reason)
                .build();

        ResolveIntResponse intResponse = ResolveIntResponse.newBuilder()
                .setValue(INT_VALUE)
                .setVariant(INT_VARIANT)
                .setReason(reason)
                .build();

        ResolveFloatResponse floatResponse = ResolveFloatResponse.newBuilder()
                .setValue(DOUBLE_VALUE)
                .setVariant(DOUBLE_VARIANT)
                .setReason(reason)
                .build();

        ResolveObjectResponse objectResponse = ResolveObjectResponse.newBuilder()
                .setValue(PROTOBUF_STRUCTURE_VALUE)
                .setVariant(OBJECT_VARIANT)
                .setReason(reason)
                .build();

        ServiceBlockingStub serviceBlockingStubMock = mock(ServiceBlockingStub.class);
        ServiceStub serviceStubMock = mock(ServiceStub.class);
        when(serviceBlockingStubMock.withDeadlineAfter(anyLong(), any(TimeUnit.class)))
                .thenReturn(serviceBlockingStubMock);
        when(serviceBlockingStubMock
                .resolveBoolean(argThat(x -> FLAG_KEY_BOOLEAN.equals(x.getFlagKey())))).thenReturn(booleanResponse);
        when(serviceBlockingStubMock
                .resolveFloat(argThat(x -> FLAG_KEY_DOUBLE.equals(x.getFlagKey())))).thenReturn(floatResponse);
        when(serviceBlockingStubMock
                .resolveInt(argThat(x -> FLAG_KEY_INTEGER.equals(x.getFlagKey())))).thenReturn(intResponse);
        when(serviceBlockingStubMock
                .resolveString(argThat(x -> FLAG_KEY_STRING.equals(x.getFlagKey())))).thenReturn(stringResponse);
        when(serviceBlockingStubMock
                .resolveObject(argThat(x -> FLAG_KEY_OBJECT.equals(x.getFlagKey())))).thenReturn(objectResponse);

        FlagdProvider provider = new FlagdProvider(serviceBlockingStubMock, serviceStubMock, "lru", 100, 5);
        provider.setState(eventStreamAlive); // caching only available when event stream is alive
        OpenFeatureAPI.getInstance().setProvider(provider);

        FlagEvaluationDetails<Boolean> booleanDetails = api.getClient().getBooleanDetails(FLAG_KEY_BOOLEAN, false);
        booleanDetails = api.getClient().getBooleanDetails(FLAG_KEY_BOOLEAN, false); // should retrieve from cache on second invocation
        assertTrue(booleanDetails.getValue());
        assertEquals(BOOL_VARIANT, booleanDetails.getVariant());
        assertEquals(expectedReason, booleanDetails.getReason());

        FlagEvaluationDetails<String> stringDetails = api.getClient().getStringDetails(FLAG_KEY_STRING, "wrong");
        stringDetails = api.getClient().getStringDetails(FLAG_KEY_STRING, "wrong");
        assertEquals(STRING_VALUE, stringDetails.getValue());
        assertEquals(STRING_VARIANT, stringDetails.getVariant());
        assertEquals(expectedReason, stringDetails.getReason());

        FlagEvaluationDetails<Integer> intDetails = api.getClient().getIntegerDetails(FLAG_KEY_INTEGER, 0);
        intDetails = api.getClient().getIntegerDetails(FLAG_KEY_INTEGER, 0);
        assertEquals(INT_VALUE, intDetails.getValue());
        assertEquals(INT_VARIANT, intDetails.getVariant());
        assertEquals(expectedReason, intDetails.getReason());

        FlagEvaluationDetails<Double> floatDetails = api.getClient().getDoubleDetails(FLAG_KEY_DOUBLE, 0.1);
        floatDetails = api.getClient().getDoubleDetails(FLAG_KEY_DOUBLE, 0.1);
        assertEquals(DOUBLE_VALUE, floatDetails.getValue());
        assertEquals(DOUBLE_VARIANT, floatDetails.getVariant());
        assertEquals(expectedReason, floatDetails.getReason());

        FlagEvaluationDetails<Value> objectDetails = api.getClient().getObjectDetails(FLAG_KEY_OBJECT, new Value());
        objectDetails = api.getClient().getObjectDetails(FLAG_KEY_OBJECT, new Value());
        assertEquals(INNER_STRUCT_VALUE, objectDetails.getValue().asStructure()
                .asMap().get(INNER_STRUCT_KEY).asString());
        assertEquals(OBJECT_VARIANT, objectDetails.getVariant());
        assertEquals(expectedReason, objectDetails.getReason());
    }
    
    @Test
    void disabled_cache() {
        ResolveBooleanResponse booleanResponse = ResolveBooleanResponse.newBuilder()
                .setValue(true)
                .setVariant(BOOL_VARIANT)
                .setReason(STATIC_REASON)
                .build();

        ResolveStringResponse stringResponse = ResolveStringResponse.newBuilder()
                .setValue(STRING_VALUE)
                .setVariant(STRING_VARIANT)
                .setReason(STATIC_REASON)
                .build();

        ResolveIntResponse intResponse = ResolveIntResponse.newBuilder()
                .setValue(INT_VALUE)
                .setVariant(INT_VARIANT)
                .setReason(STATIC_REASON)
                .build();

        ResolveFloatResponse floatResponse = ResolveFloatResponse.newBuilder()
                .setValue(DOUBLE_VALUE)
                .setVariant(DOUBLE_VARIANT)
                .setReason(STATIC_REASON)
                .build();

        ResolveObjectResponse objectResponse = ResolveObjectResponse.newBuilder()
                .setValue(PROTOBUF_STRUCTURE_VALUE)
                .setVariant(OBJECT_VARIANT)
                .setReason(STATIC_REASON)
                .build();


        ServiceBlockingStub serviceBlockingStubMock = mock(ServiceBlockingStub.class);
        ServiceStub serviceStubMock = mock(ServiceStub.class);
        when(serviceStubMock.withWaitForReady()).thenReturn(serviceStubMock);
        when(serviceStubMock.withDeadline(any(Deadline.class)))
                .thenReturn(serviceStubMock);
        when(serviceBlockingStubMock.withWaitForReady()).thenReturn(serviceBlockingStubMock);
        when(serviceBlockingStubMock.withDeadline(any(Deadline.class)))
                .thenReturn(serviceBlockingStubMock);
        when(serviceBlockingStubMock.withDeadlineAfter(anyLong(), any(TimeUnit.class)))
                .thenReturn(serviceBlockingStubMock);
        when(serviceBlockingStubMock
                .resolveBoolean(argThat(x -> FLAG_KEY_BOOLEAN.equals(x.getFlagKey())))).thenReturn(booleanResponse);
        when(serviceBlockingStubMock
                .resolveFloat(argThat(x -> FLAG_KEY_DOUBLE.equals(x.getFlagKey())))).thenReturn(floatResponse);
        when(serviceBlockingStubMock
                .resolveInt(argThat(x -> FLAG_KEY_INTEGER.equals(x.getFlagKey())))).thenReturn(intResponse);
        when(serviceBlockingStubMock
                .resolveString(argThat(x -> FLAG_KEY_STRING.equals(x.getFlagKey())))).thenReturn(stringResponse);
        when(serviceBlockingStubMock
                .resolveObject(argThat(x -> FLAG_KEY_OBJECT.equals(x.getFlagKey())))).thenReturn(objectResponse);

        FlagdProvider provider = new FlagdProvider(serviceBlockingStubMock, serviceStubMock, null, 0, 1);
        provider.initialize(null);
        ArgumentCaptor<StreamObserver<EventStreamResponse>> streamObserverCaptor = ArgumentCaptor.forClass(StreamObserver.class);
        verify(serviceStubMock).eventStream(any(EventStreamRequest.class), streamObserverCaptor.capture());

        provider.setState(ProviderState.READY);
        OpenFeatureAPI.getInstance().setProvider(provider);

        HashMap<String, com.google.protobuf.Value> flagsMap = new HashMap<String, com.google.protobuf.Value>();
        HashMap<String, com.google.protobuf.Value> structMap = new HashMap<String, com.google.protobuf.Value>();

        flagsMap.put("foo", com.google.protobuf.Value.newBuilder().setStringValue("foo").build()); // assert that a configuration_change event works

        structMap.put("flags", com.google.protobuf.Value.newBuilder().
                setStructValue(Struct.newBuilder().putAllFields(flagsMap)).build());

        EventStreamResponse eResponse = EventStreamResponse.newBuilder()
                .setType("configuration_change")
                .setData(Struct.newBuilder().putAllFields(structMap).build())
                .build();
        
        // should not cache results
        FlagEvaluationDetails<Boolean> booleanDetails = api.getClient().getBooleanDetails(FLAG_KEY_BOOLEAN, false);
        FlagEvaluationDetails<String> stringDetails = api.getClient().getStringDetails(FLAG_KEY_STRING, "wrong");
        FlagEvaluationDetails<Integer> intDetails = api.getClient().getIntegerDetails(FLAG_KEY_INTEGER, 0);
        FlagEvaluationDetails<Double> floatDetails = api.getClient().getDoubleDetails(FLAG_KEY_DOUBLE, 0.1);
        FlagEvaluationDetails<Value> objectDetails = api.getClient().getObjectDetails(FLAG_KEY_OBJECT, new Value());

        // should not cause a change of state
        streamObserverCaptor.getValue().onNext(eResponse);

        // assert values are not cached
        booleanDetails = api.getClient().getBooleanDetails(FLAG_KEY_BOOLEAN, false);
        assertTrue(booleanDetails.getValue());
        assertEquals(BOOL_VARIANT, booleanDetails.getVariant());
        assertEquals(STATIC_REASON, booleanDetails.getReason());

        stringDetails = api.getClient().getStringDetails(FLAG_KEY_STRING, "wrong");
        assertEquals(STRING_VALUE, stringDetails.getValue());
        assertEquals(STRING_VARIANT, stringDetails.getVariant());
        assertEquals(STATIC_REASON, stringDetails.getReason());

        intDetails = api.getClient().getIntegerDetails(FLAG_KEY_INTEGER, 0);
        assertEquals(INT_VALUE, intDetails.getValue());
        assertEquals(INT_VARIANT, intDetails.getVariant());
        assertEquals(STATIC_REASON, intDetails.getReason());

        floatDetails = api.getClient().getDoubleDetails(FLAG_KEY_DOUBLE, 0.1);
        assertEquals(DOUBLE_VALUE, floatDetails.getValue());
        assertEquals(DOUBLE_VARIANT, floatDetails.getVariant());
        assertEquals(STATIC_REASON, floatDetails.getReason());

        objectDetails = api.getClient().getObjectDetails(FLAG_KEY_OBJECT, new Value());
        assertEquals(INNER_STRUCT_VALUE, objectDetails.getValue().asStructure()
                .asMap().get(INNER_STRUCT_KEY).asString());
        assertEquals(OBJECT_VARIANT, objectDetails.getVariant());
        assertEquals(STATIC_REASON, objectDetails.getReason());
    }

}
