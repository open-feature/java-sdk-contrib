package dev.openfeature.contrib.providers.flagd.resolver.process;

import static dev.openfeature.contrib.providers.flagd.resolver.process.MockFlags.BOOLEAN_FLAG;
import static dev.openfeature.contrib.providers.flagd.resolver.process.MockFlags.DISABLED_FLAG;
import static dev.openfeature.contrib.providers.flagd.resolver.process.MockFlags.DOUBLE_FLAG;
import static dev.openfeature.contrib.providers.flagd.resolver.process.MockFlags.FLAG_WIH_IF_IN_TARGET;
import static dev.openfeature.contrib.providers.flagd.resolver.process.MockFlags.FLAG_WIH_INVALID_TARGET;
import static dev.openfeature.contrib.providers.flagd.resolver.process.MockFlags.FLAG_WIH_SHORTHAND_TARGETING;
import static dev.openfeature.contrib.providers.flagd.resolver.process.MockFlags.FLAG_WITH_TARGETING_KEY;
import static dev.openfeature.contrib.providers.flagd.resolver.process.MockFlags.INT_FLAG;
import static dev.openfeature.contrib.providers.flagd.resolver.process.MockFlags.OBJECT_FLAG;
import static dev.openfeature.contrib.providers.flagd.resolver.process.MockFlags.VARIANT_MISMATCH_FLAG;
import static dev.openfeature.contrib.providers.flagd.resolver.process.MockFlags.stringVariants;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import dev.openfeature.contrib.providers.flagd.Config;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.resolver.process.model.FeatureFlag;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.MockConnector;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.StorageState;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.StorageStateChange;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.file.FileQueueSource;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.sync.SyncStreamQueueSource;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.ImmutableMetadata;
import dev.openfeature.sdk.MutableContext;
import dev.openfeature.sdk.MutableStructure;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.ProviderEvent;
import dev.openfeature.sdk.ProviderEventDetails;
import dev.openfeature.sdk.Reason;
import dev.openfeature.sdk.Structure;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.GeneralError;
import dev.openfeature.sdk.exceptions.ParseError;
import dev.openfeature.sdk.exceptions.TypeMismatchError;
import dev.openfeature.sdk.internal.TriConsumer;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class InProcessResolverTest {
    @Test
    void onError_delegatesToQueueSource() throws Exception {
        // given
        FlagdOptions options = FlagdOptions.builder().build(); // option value doesn't matter here
        SyncStreamQueueSource mockConnector = mock(SyncStreamQueueSource.class);
        InProcessResolver resolver = new InProcessResolver(options, (event, details, metadata) -> {});

        // Inject mock connector
        java.lang.reflect.Field queueSourceField = InProcessResolver.class.getDeclaredField("queueSource");
        queueSourceField.setAccessible(true);
        queueSourceField.set(resolver, mockConnector);

        // when
        resolver.onError();

        // then
        // InProcessResolver should always delegate to the queue source.
        // The decision to re-initialize or not is handled within SyncStreamQueueSource.
        verify(mockConnector, times(1)).reinitializeChannelComponents();
    }

    @Test
    public void connectorSetup() {
        // given
        FlagdOptions forGrpcOptions = FlagdOptions.builder()
                .resolverType(Config.Resolver.IN_PROCESS)
                .host("localhost")
                .port(8080)
                .build();
        FlagdOptions forOfflineOptions = FlagdOptions.builder()
                .resolverType(Config.Resolver.IN_PROCESS)
                .offlineFlagSourcePath("path")
                .build();
        FlagdOptions forCustomConnectorOptions = FlagdOptions.builder()
                .resolverType(Config.Resolver.IN_PROCESS)
                .customConnector(new MockConnector(null))
                .build();

        // then
        assertInstanceOf(SyncStreamQueueSource.class, InProcessResolver.getQueueSource(forGrpcOptions));
        assertInstanceOf(FileQueueSource.class, InProcessResolver.getQueueSource(forOfflineOptions));
        assertInstanceOf(MockConnector.class, InProcessResolver.getQueueSource(forCustomConnectorOptions));
    }

    @Test
    void eventHandling() throws Throwable {
        // given
        // note - queues with adequate capacity
        final BlockingQueue<StorageStateChange> sender = new LinkedBlockingQueue<>(5);
        final BlockingQueue<StorageStateChange> receiver = new LinkedBlockingQueue<>(5);
        final String key = "key1";
        final String val = "val1";
        final MutableStructure syncMetadata = new MutableStructure();
        syncMetadata.add(key, val);

        InProcessResolver inProcessResolver =
                getInProcessResolverWith(new MockStorage(new HashMap<>(), sender), (event, details, metadata) -> {
                    boolean isDisconnected =
                            event == ProviderEvent.PROVIDER_ERROR || event == ProviderEvent.PROVIDER_STALE;
                    receiver.offer(new StorageStateChange(
                            isDisconnected ? StorageState.ERROR : StorageState.OK,
                            details != null ? details.getFlagsChanged() : Collections.emptyList(),
                            metadata));
                });

        // when - init and emit events
        Thread initThread = new Thread(() -> {
            try {
                inProcessResolver.init();
            } catch (Exception e) {
            }
        });
        initThread.start();
        if (!sender.offer(
                new StorageStateChange(StorageState.OK, Collections.emptyList(), syncMetadata),
                100,
                TimeUnit.MILLISECONDS)) {
            Assertions.fail("failed to send the event");
        }
        if (!sender.offer(new StorageStateChange(StorageState.ERROR), 100, TimeUnit.MILLISECONDS)) {
            Assertions.fail("failed to send the event");
        }

        // then - receive events in order
        assertTimeoutPreemptively(Duration.ofMillis(200), () -> {
            StorageStateChange storageState = receiver.take();
            assertEquals(StorageState.OK, storageState.getStorageState());
            assertEquals(val, storageState.getSyncMetadata().getValue(key).asString());
        });
    }

    @Test
    public void simpleBooleanResolving() throws Exception {
        // given
        final Map<String, FeatureFlag> flagMap = new HashMap<>();
        flagMap.put("booleanFlag", BOOLEAN_FLAG);

        InProcessResolver inProcessResolver =
                getInProcessResolverWith(new MockStorage(flagMap), (event, details, metadata) -> {});

        // when
        ProviderEvaluation<Boolean> providerEvaluation =
                inProcessResolver.booleanEvaluation("booleanFlag", false, new ImmutableContext());

        // then
        assertEquals(true, providerEvaluation.getValue());
        assertEquals("on", providerEvaluation.getVariant());
        assertEquals(Reason.STATIC.toString(), providerEvaluation.getReason());
    }

    @Test
    public void simpleDoubleResolving() throws Exception {
        // given
        final Map<String, FeatureFlag> flagMap = new HashMap<>();
        flagMap.put("doubleFlag", DOUBLE_FLAG);

        InProcessResolver inProcessResolver =
                getInProcessResolverWith(new MockStorage(flagMap), (event, details, metadata) -> {});

        // when
        ProviderEvaluation<Double> providerEvaluation =
                inProcessResolver.doubleEvaluation("doubleFlag", 0d, new ImmutableContext());

        // then
        assertEquals(3.141d, providerEvaluation.getValue());
        assertEquals("one", providerEvaluation.getVariant());
        assertEquals(Reason.STATIC.toString(), providerEvaluation.getReason());
    }

    @Test
    public void fetchIntegerAsDouble() throws Exception {
        // given
        final Map<String, FeatureFlag> flagMap = new HashMap<>();
        flagMap.put("doubleFlag", DOUBLE_FLAG);

        InProcessResolver inProcessResolver =
                getInProcessResolverWith(new MockStorage(flagMap), (event, details, metadata) -> {});

        // when
        ProviderEvaluation<Integer> providerEvaluation =
                inProcessResolver.integerEvaluation("doubleFlag", 0, new ImmutableContext());

        // then
        assertEquals(3, providerEvaluation.getValue());
        assertEquals("one", providerEvaluation.getVariant());
        assertEquals(Reason.STATIC.toString(), providerEvaluation.getReason());
    }

    @Test
    public void fetchDoubleAsInt() throws Exception {
        // given
        final Map<String, FeatureFlag> flagMap = new HashMap<>();
        flagMap.put("integerFlag", INT_FLAG);

        InProcessResolver inProcessResolver =
                getInProcessResolverWith(new MockStorage(flagMap), (event, details, metadata) -> {});

        // when
        ProviderEvaluation<Double> providerEvaluation =
                inProcessResolver.doubleEvaluation("integerFlag", 0d, new ImmutableContext());

        // then
        assertEquals(1d, providerEvaluation.getValue());
        assertEquals("one", providerEvaluation.getVariant());
        assertEquals(Reason.STATIC.toString(), providerEvaluation.getReason());
    }

    @Test
    public void simpleIntResolving() throws Exception {
        // given
        final Map<String, FeatureFlag> flagMap = new HashMap<>();
        flagMap.put("integerFlag", INT_FLAG);

        InProcessResolver inProcessResolver =
                getInProcessResolverWith(new MockStorage(flagMap), (event, details, metadata) -> {});

        // when
        ProviderEvaluation<Integer> providerEvaluation =
                inProcessResolver.integerEvaluation("integerFlag", 0, new ImmutableContext());

        // then
        assertEquals(1, providerEvaluation.getValue());
        assertEquals("one", providerEvaluation.getVariant());
        assertEquals(Reason.STATIC.toString(), providerEvaluation.getReason());
    }

    @Test
    public void simpleObjectResolving() throws Exception {
        // given
        final Map<String, FeatureFlag> flagMap = new HashMap<>();
        flagMap.put("objectFlag", OBJECT_FLAG);

        InProcessResolver inProcessResolver =
                getInProcessResolverWith(new MockStorage(flagMap), (event, details, metadata) -> {});

        Map<String, Object> typeDefault = new HashMap<>();
        typeDefault.put("key", "0164");
        typeDefault.put("date", "01.01.1990");

        // when
        ProviderEvaluation<Value> providerEvaluation = inProcessResolver.objectEvaluation(
                "objectFlag", Value.objectToValue(typeDefault), new ImmutableContext());

        // then
        Value value = providerEvaluation.getValue();
        Map<String, Value> valueMap = value.asStructure().asMap();

        assertEquals("0165", valueMap.get("key").asString());
        assertEquals("01.01.2000", valueMap.get("date").asString());
        assertEquals(Reason.STATIC.toString(), providerEvaluation.getReason());
        assertEquals("typeA", providerEvaluation.getVariant());
    }

    @Test
    public void missingFlag() throws Exception {
        // given
        final Map<String, FeatureFlag> flagMap = new HashMap<>();

        InProcessResolver inProcessResolver =
                getInProcessResolverWith(new MockStorage(flagMap), (event, details, metadata) -> {});

        // when/then
        ProviderEvaluation<Boolean> missingFlag =
                inProcessResolver.booleanEvaluation("missingFlag", false, new ImmutableContext());
        assertEquals(ErrorCode.FLAG_NOT_FOUND, missingFlag.getErrorCode());
    }

    @Test
    public void disabledFlag() throws Exception {
        // given
        final Map<String, FeatureFlag> flagMap = new HashMap<>();
        flagMap.put("disabledFlag", DISABLED_FLAG);

        InProcessResolver inProcessResolver =
                getInProcessResolverWith(new MockStorage(flagMap), (event, details, metadata) -> {});

        // when/then
        ProviderEvaluation<Boolean> disabledFlag =
                inProcessResolver.booleanEvaluation("disabledFlag", false, new ImmutableContext());
        assertEquals(ErrorCode.FLAG_NOT_FOUND, disabledFlag.getErrorCode());
    }

    @Test
    public void variantMismatchFlag() throws Exception {
        // given
        final Map<String, FeatureFlag> flagMap = new HashMap<>();
        flagMap.put("mismatchFlag", VARIANT_MISMATCH_FLAG);

        InProcessResolver inProcessResolver =
                getInProcessResolverWith(new MockStorage(flagMap), (event, details, metadata) -> {});

        // when/then
        assertThrows(GeneralError.class, () -> {
            inProcessResolver.booleanEvaluation("mismatchFlag", false, new ImmutableContext());
        });
    }

    @Test
    public void typeMismatchEvaluation() throws Exception {
        // given
        final Map<String, FeatureFlag> flagMap = new HashMap<>();
        flagMap.put("booleanFlag", BOOLEAN_FLAG);

        InProcessResolver inProcessResolver =
                getInProcessResolverWith(new MockStorage(flagMap), (event, details, metadata) -> {});

        // when/then
        assertThrows(TypeMismatchError.class, () -> {
            inProcessResolver.stringEvaluation("booleanFlag", "false", new ImmutableContext());
        });
    }

    @Test
    public void booleanShorthandEvaluation() throws Exception {
        // given
        final Map<String, FeatureFlag> flagMap = new HashMap<>();
        flagMap.put("shorthand", FLAG_WIH_SHORTHAND_TARGETING);

        InProcessResolver inProcessResolver =
                getInProcessResolverWith(new MockStorage(flagMap), (event, details, metadata) -> {});

        ProviderEvaluation<Boolean> providerEvaluation =
                inProcessResolver.booleanEvaluation("shorthand", false, new ImmutableContext());

        // then
        assertEquals(true, providerEvaluation.getValue());
        assertEquals("true", providerEvaluation.getVariant());
        assertEquals(Reason.TARGETING_MATCH.toString(), providerEvaluation.getReason());
    }

    @Test
    public void targetingMatchedEvaluationFlag() throws Exception {
        // given
        final Map<String, FeatureFlag> flagMap = new HashMap<>();
        flagMap.put("stringFlag", FLAG_WIH_IF_IN_TARGET);

        InProcessResolver inProcessResolver =
                getInProcessResolverWith(new MockStorage(flagMap), (event, details, metadata) -> {});

        // when
        ProviderEvaluation<String> providerEvaluation = inProcessResolver.stringEvaluation(
                "stringFlag", "loopAlg", new MutableContext().add("email", "abc@faas.com"));

        // then
        assertEquals("binetAlg", providerEvaluation.getValue());
        assertEquals("binet", providerEvaluation.getVariant());
        assertEquals(Reason.TARGETING_MATCH.toString(), providerEvaluation.getReason());
    }

    @Test
    public void targetingUnmatchedEvaluationFlag() throws Exception {
        // given
        final Map<String, FeatureFlag> flagMap = new HashMap<>();
        flagMap.put("stringFlag", FLAG_WIH_IF_IN_TARGET);

        InProcessResolver inProcessResolver =
                getInProcessResolverWith(new MockStorage(flagMap), (event, details, metadata) -> {});

        // when
        ProviderEvaluation<String> providerEvaluation = inProcessResolver.stringEvaluation(
                "stringFlag", "loopAlg", new MutableContext().add("email", "abc@abc.com"));

        // then
        assertEquals("loopAlg", providerEvaluation.getValue());
        assertEquals("loop", providerEvaluation.getVariant());
        assertEquals(Reason.DEFAULT.toString(), providerEvaluation.getReason());
    }

    @Test
    public void explicitTargetingKeyHandling() throws NoSuchFieldException, IllegalAccessException {
        // given
        final Map<String, FeatureFlag> flagMap = new HashMap<>();
        flagMap.put("stringFlag", FLAG_WITH_TARGETING_KEY);

        InProcessResolver inProcessResolver =
                getInProcessResolverWith(new MockStorage(flagMap), (event, details, metadata) -> {});

        // when
        ProviderEvaluation<String> providerEvaluation =
                inProcessResolver.stringEvaluation("stringFlag", "loop", new MutableContext("xyz"));

        // then
        assertEquals("binetAlg", providerEvaluation.getValue());
        assertEquals("binet", providerEvaluation.getVariant());
        assertEquals(Reason.TARGETING_MATCH.toString(), providerEvaluation.getReason());
    }

    @Test
    public void targetingErrorEvaluationFlag() throws Exception {
        // given
        final Map<String, FeatureFlag> flagMap = new HashMap<>();
        flagMap.put("targetingErrorFlag", FLAG_WIH_INVALID_TARGET);

        InProcessResolver inProcessResolver =
                getInProcessResolverWith(new MockStorage(flagMap), (event, details, metadata) -> {});

        // when/then
        assertThrows(ParseError.class, () -> {
            inProcessResolver.booleanEvaluation("targetingErrorFlag", false, new ImmutableContext());
        });
    }

    @Test
    public void validateMetadataInEvaluationResult() throws Exception {
        // given
        final String scope = "appName=myApp";
        final Map<String, FeatureFlag> flagMap = new HashMap<>();
        flagMap.put("booleanFlag", BOOLEAN_FLAG);

        InProcessResolver inProcessResolver =
                getInProcessResolverWith(FlagdOptions.builder().selector(scope).build(), new MockStorage(flagMap));

        // when
        ProviderEvaluation<Boolean> providerEvaluation =
                inProcessResolver.booleanEvaluation("booleanFlag", false, new ImmutableContext());

        // then
        final ImmutableMetadata metadata = providerEvaluation.getFlagMetadata();
        assertNotNull(metadata);
        assertEquals(scope, metadata.getString("scope"));
    }

    @Test
    void selectorIsAddedToFlagMetadata() throws Exception {
        // given
        final Map<String, FeatureFlag> flagMap = new HashMap<>();
        flagMap.put("flag", INT_FLAG);

        InProcessResolver inProcessResolver =
                getInProcessResolverWith(new MockStorage(flagMap), (event, details, metadata) -> {}, "selector");

        // when
        ProviderEvaluation<Integer> providerEvaluation =
                inProcessResolver.integerEvaluation("flag", 0, new ImmutableContext());

        // then
        assertThat(providerEvaluation.getFlagMetadata()).isNotNull();
        assertThat(providerEvaluation.getFlagMetadata().getString("scope")).isEqualTo("selector");
    }

    @Test
    void selectorIsOverwrittenByFlagMetadata() throws Exception {
        // given
        final Map<String, FeatureFlag> flagMap = new HashMap<>();
        final Map<String, Object> flagMetadata = new HashMap<>();
        flagMetadata.put("scope", "new selector");
        flagMap.put("flag", new FeatureFlag("stage", "loop", stringVariants, "", flagMetadata));

        InProcessResolver inProcessResolver =
                getInProcessResolverWith(new MockStorage(flagMap), (event, details, metadata) -> {}, "selector");

        // when
        ProviderEvaluation<String> providerEvaluation =
                inProcessResolver.stringEvaluation("flag", "def", new ImmutableContext());

        // then
        assertThat(providerEvaluation.getFlagMetadata()).isNotNull();
        assertThat(providerEvaluation.getFlagMetadata().getString("scope")).isEqualTo("new selector");
    }

    @Test
    void flagSetMetadataIsAddedToEvaluation() throws Exception {
        // given
        final Map<String, FeatureFlag> flagMap = new HashMap<>();
        final Map<String, Object> flagMetadata = new HashMap<>();
        flagMetadata.put("scope", "new selector");
        flagMap.put("flag", new FeatureFlag("stage", "loop", stringVariants, "", flagMetadata));

        final Map<String, Object> flagSetMetadata = new HashMap<>();
        flagSetMetadata.put("flagSetMetadata", "metadata");
        InProcessResolver inProcessResolver = getInProcessResolverWith(
                new MockStorage(flagMap, flagSetMetadata), (event, details, metadata) -> {}, "selector");

        // when
        ProviderEvaluation<String> providerEvaluation =
                inProcessResolver.stringEvaluation("flag", "def", new ImmutableContext());

        // then
        assertThat(providerEvaluation.getFlagMetadata()).isNotNull();
        assertThat(providerEvaluation.getFlagMetadata().getString("scope")).isEqualTo("new selector");
        assertThat(providerEvaluation.getFlagMetadata().getString("flagSetMetadata"))
                .isEqualTo("metadata");
    }

    @Test
    void flagSetMetadataIsAddedToFailingEvaluation() throws Exception {
        // given
        final Map<String, FeatureFlag> flagMap = new HashMap<>();

        final Map<String, Object> flagSetMetadata = new HashMap<>();
        flagSetMetadata.put("flagSetMetadata", "metadata");
        InProcessResolver inProcessResolver = getInProcessResolverWith(
                new MockStorage(flagMap, flagSetMetadata), (event, details, metadata) -> {}, "selector");

        // when
        ProviderEvaluation<String> providerEvaluation =
                inProcessResolver.stringEvaluation("does not exist", "def", new ImmutableContext());

        // then
        assertThat(providerEvaluation.getReason()).isNull();
        assertThat(providerEvaluation.getFlagMetadata()).isNotNull();
        assertThat(providerEvaluation.getFlagMetadata().getString("flagSetMetadata"))
                .isEqualTo("metadata");
    }

    @Test
    void flagSetMetadataIsOverwrittenByFlagMetadataToEvaluation() throws Exception {
        // given
        final Map<String, FeatureFlag> flagMap = new HashMap<>();
        final Map<String, Object> flagMetadata = new HashMap<>();
        flagMetadata.put("key", "expected");
        flagMap.put("flag", new FeatureFlag("stage", "loop", stringVariants, "", flagMetadata));

        final Map<String, Object> flagSetMetadata = new HashMap<>();
        flagSetMetadata.put("key", "unexpected");
        InProcessResolver inProcessResolver = getInProcessResolverWith(
                new MockStorage(flagMap, flagSetMetadata), (event, details, metadata) -> {}, "selector");

        // when
        ProviderEvaluation<String> providerEvaluation =
                inProcessResolver.stringEvaluation("flag", "def", new ImmutableContext());

        // then
        assertThat(providerEvaluation.getFlagMetadata()).isNotNull();
        assertThat(providerEvaluation.getFlagMetadata().getString("key")).isEqualTo("expected");
    }

    @Test
    void testStateWatcherThreadIsCleanedUpDuringShutdown() throws Exception {
        // given
        final Map<String, FeatureFlag> flagMap = new HashMap<>();
        flagMap.put("booleanFlag", BOOLEAN_FLAG);

        var initialThreadCount = currentDaemonThreadCount();

        var queue = new LinkedBlockingQueue<StorageStateChange>();
        InProcessResolver inProcessResolver =
                getInProcessResolverWith(new MockStorage(flagMap, queue), (event, details, metadata) -> {});

        // when
        inProcessResolver.init();
        Thread stateWatcher = Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> InProcessResolver.STATE_WATCHER_THREAD_NAME.equals(thread.getName()))
                .findFirst()
                .orElseThrow();
        var threadCountAfterInit = currentDaemonThreadCount();
        var stateWatcherWasStarted = stateWatcher.isAlive();
        inProcessResolver.shutdown();

        // then
        assertThat(stateWatcherWasStarted).isTrue();
        assertThat(threadCountAfterInit).isGreaterThan(initialThreadCount);
        Awaitility.await().until(() -> !stateWatcher.isAlive());
        assertThat(currentDaemonThreadCount()).isEqualTo(initialThreadCount);
    }

    private long currentDaemonThreadCount() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(Thread::isDaemon)
                .count();
    }

    private InProcessResolver getInProcessResolverWith(final FlagdOptions options, final MockStorage storage)
            throws NoSuchFieldException, IllegalAccessException {

        final InProcessResolver resolver = new InProcessResolver(options, (event, details, metadata) -> {});
        return injectFlagStore(resolver, storage);
    }

    private InProcessResolver getInProcessResolverWith(
            final MockStorage storage,
            final TriConsumer<ProviderEvent, ProviderEventDetails, Structure> onConnectionEvent)
            throws NoSuchFieldException, IllegalAccessException {

        final InProcessResolver resolver =
                new InProcessResolver(FlagdOptions.builder().deadline(1000).build(), onConnectionEvent);
        return injectFlagStore(resolver, storage);
    }

    private InProcessResolver getInProcessResolverWith(
            final MockStorage storage,
            final TriConsumer<ProviderEvent, ProviderEventDetails, Structure> onConnectionEvent,
            String selector)
            throws NoSuchFieldException, IllegalAccessException {

        final InProcessResolver resolver = new InProcessResolver(
                FlagdOptions.builder().selector(selector).deadline(1000).build(), onConnectionEvent);
        return injectFlagStore(resolver, storage);
    }

    // helper to inject flagStore override
    private InProcessResolver injectFlagStore(final InProcessResolver resolver, final MockStorage storage)
            throws NoSuchFieldException, IllegalAccessException {

        final Field flagStore = InProcessResolver.class.getDeclaredField("flagStore");
        flagStore.setAccessible(true);
        flagStore.set(resolver, storage);

        return resolver;
    }
}
