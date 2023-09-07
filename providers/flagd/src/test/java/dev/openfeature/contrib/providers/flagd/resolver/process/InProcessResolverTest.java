package dev.openfeature.contrib.providers.flagd.resolver.process;

import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.resolver.process.model.FeatureFlag;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.StorageState;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.MutableContext;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.ProviderState;
import dev.openfeature.sdk.Reason;
import dev.openfeature.sdk.Value;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static dev.openfeature.contrib.providers.flagd.resolver.process.MockFlags.BOOLEAN_FLAG;
import static dev.openfeature.contrib.providers.flagd.resolver.process.MockFlags.DISABLED_FLAG;
import static dev.openfeature.contrib.providers.flagd.resolver.process.MockFlags.FLAG_WIH_IF_IN_TARGET;
import static dev.openfeature.contrib.providers.flagd.resolver.process.MockFlags.FLAG_WIH_INVALID_TARGET;
import static dev.openfeature.contrib.providers.flagd.resolver.process.MockFlags.OBJECT_FLAG;
import static dev.openfeature.contrib.providers.flagd.resolver.process.MockFlags.VARIANT_MISMATCH_FLAG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class InProcessResolverTest {

    @Test
    public void eventHandling() throws Throwable {
        // given
        // note - queues with adequate capacity
        final BlockingQueue<StorageState> sender = new LinkedBlockingQueue<>(5);
        final BlockingQueue<ProviderState> receiver = new LinkedBlockingQueue<>(5);

        InProcessResolver inProcessResolver =
                getInProcessResolverWth(new MockStorage(new HashMap<>(), sender), providerState -> {
                    receiver.offer(providerState);
                });

        // when
        inProcessResolver.init();
        if (!sender.offer(StorageState.OK, 200, TimeUnit.MILLISECONDS)) {
            Assertions.fail("failed to send the event");
        }

        if (!sender.offer(StorageState.ERROR, 200, TimeUnit.MILLISECONDS)) {
            Assertions.fail("failed to send the event");
        }

        // then
        assertTimeoutPreemptively(Duration.ofMillis(200), () -> {
            Assertions.assertEquals(ProviderState.READY, receiver.take());
        });

        assertTimeoutPreemptively(Duration.ofMillis(200), () -> {
            Assertions.assertEquals(ProviderState.ERROR, receiver.take());
        });
    }

    @Test
    public void simpleResolving() throws Exception {
        // given
        final Map<String, FeatureFlag> flagMap = new HashMap<>();
        flagMap.put("booleanFlag", BOOLEAN_FLAG);

        InProcessResolver inProcessResolver = getInProcessResolverWth(new MockStorage(flagMap), providerState -> {
        });

        // when
        ProviderEvaluation<Boolean> providerEvaluation =
                inProcessResolver.booleanEvaluation("booleanFlag", false, new ImmutableContext());

        // then
        assertEquals(true, providerEvaluation.getValue());
        assertEquals("on", providerEvaluation.getVariant());
        assertEquals(Reason.STATIC.toString(), providerEvaluation.getReason());
    }

    @Test
    public void simpleObjectResolving() throws Exception {
        // given
        final Map<String, FeatureFlag> flagMap = new HashMap<>();
        flagMap.put("objectFlag", OBJECT_FLAG);

        InProcessResolver inProcessResolver = getInProcessResolverWth(new MockStorage(flagMap), providerState -> {
        });

        Map<String, Object> typeDefault = new HashMap<>();
        typeDefault.put("key", "0164");
        typeDefault.put("date", "01.01.1990");

        // when
        ProviderEvaluation<Value> providerEvaluation =
                inProcessResolver.objectEvaluation("objectFlag", Value.objectToValue(typeDefault), new ImmutableContext());

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

        InProcessResolver inProcessResolver = getInProcessResolverWth(new MockStorage(flagMap), providerState -> {
        });

        // when
        ProviderEvaluation<Boolean> providerEvaluation =
                inProcessResolver.booleanEvaluation("booleanFlag", false, new ImmutableContext());

        // then
        assertEquals(false, providerEvaluation.getValue());
        assertEquals(Reason.ERROR.toString(), providerEvaluation.getReason());
        assertEquals(ErrorCode.FLAG_NOT_FOUND, providerEvaluation.getErrorCode());
        assertNotNull(providerEvaluation.getErrorMessage());
    }

    @Test
    public void disabledFlag() throws Exception {
        // given
        final Map<String, FeatureFlag> flagMap = new HashMap<>();
        flagMap.put("booleanFlag", DISABLED_FLAG);

        InProcessResolver inProcessResolver = getInProcessResolverWth(new MockStorage(flagMap), providerState -> {
        });

        // when
        ProviderEvaluation<Boolean> providerEvaluation =
                inProcessResolver.booleanEvaluation("booleanFlag", false, new ImmutableContext());

        // then
        assertEquals(false, providerEvaluation.getValue());
        assertEquals(Reason.DISABLED.toString(), providerEvaluation.getReason());
        assertEquals(ErrorCode.GENERAL, providerEvaluation.getErrorCode());
        assertNotNull(providerEvaluation.getErrorMessage());
    }

    @Test
    public void variantMismatchFlag() throws Exception {
        // given
        final Map<String, FeatureFlag> flagMap = new HashMap<>();
        flagMap.put("booleanFlag", VARIANT_MISMATCH_FLAG);

        InProcessResolver inProcessResolver = getInProcessResolverWth(new MockStorage(flagMap), providerState -> {
        });

        // when
        ProviderEvaluation<Boolean> providerEvaluation =
                inProcessResolver.booleanEvaluation("booleanFlag", false, new ImmutableContext());

        // then
        assertEquals(false, providerEvaluation.getValue());
        assertEquals(Reason.ERROR.toString(), providerEvaluation.getReason());
        assertEquals(ErrorCode.TYPE_MISMATCH, providerEvaluation.getErrorCode());
        assertNotNull(providerEvaluation.getErrorMessage());
    }

    @Test
    public void typeMismatchEvaluation() throws Exception {
        // given
        final Map<String, FeatureFlag> flagMap = new HashMap<>();
        flagMap.put("stringFlag", BOOLEAN_FLAG);

        InProcessResolver inProcessResolver = getInProcessResolverWth(new MockStorage(flagMap), providerState -> {
        });

        // when
        ProviderEvaluation<String> providerEvaluation =
                inProcessResolver.stringEvaluation("stringFlag", "false", new ImmutableContext());

        // then
        assertEquals("false", providerEvaluation.getValue());
        assertEquals(Reason.ERROR.toString(), providerEvaluation.getReason());
        assertEquals(ErrorCode.TYPE_MISMATCH, providerEvaluation.getErrorCode());
        assertNotNull(providerEvaluation.getErrorMessage());
    }

    @Test
    public void targetingMatchedEvaluationFlag() throws Exception {
        // given
        final Map<String, FeatureFlag> flagMap = new HashMap<>();
        flagMap.put("stringFlag", FLAG_WIH_IF_IN_TARGET);

        InProcessResolver inProcessResolver = getInProcessResolverWth(new MockStorage(flagMap), providerState -> {
        });

        // when
        ProviderEvaluation<String> providerEvaluation =
                inProcessResolver.stringEvaluation("stringFlag", "loopAlg",
                        new MutableContext().add("email", "abc@faas.com"));

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

        InProcessResolver inProcessResolver = getInProcessResolverWth(new MockStorage(flagMap), providerState -> {
        });

        // when
        ProviderEvaluation<String> providerEvaluation =
                inProcessResolver.stringEvaluation("stringFlag", "loopAlg",
                        new MutableContext().add("email", "abc@abc.com"));

        // then
        assertEquals("loopAlg", providerEvaluation.getValue());
        assertEquals("loop", providerEvaluation.getVariant());
        assertEquals(Reason.DEFAULT.toString(), providerEvaluation.getReason());
    }

    @Test
    public void targetingErrorEvaluationFlag() throws Exception {
        // given
        final Map<String, FeatureFlag> flagMap = new HashMap<>();
        flagMap.put("stringFlag", FLAG_WIH_INVALID_TARGET);

        InProcessResolver inProcessResolver = getInProcessResolverWth(new MockStorage(flagMap), providerState -> {
        });

        // when
        ProviderEvaluation<String> providerEvaluation =
                inProcessResolver.stringEvaluation("stringFlag", "loopAlg",
                        new MutableContext().add("email", "abc@abc,com"));

        // then
        assertEquals("loopAlg", providerEvaluation.getValue());
        assertEquals(Reason.ERROR.toString(), providerEvaluation.getReason());
        assertEquals(ErrorCode.PARSE_ERROR, providerEvaluation.getErrorCode());
    }


    private InProcessResolver getInProcessResolverWth(final MockStorage storage, Consumer<ProviderState> stateConsumer)
            throws NoSuchFieldException, IllegalAccessException {
        Field flagStore = InProcessResolver.class.getDeclaredField("flagStore");
        flagStore.setAccessible(true);

        InProcessResolver resolver = new InProcessResolver(FlagdOptions.builder().build(), stateConsumer);
        flagStore.set(resolver, storage);

        return resolver;
    }

}
