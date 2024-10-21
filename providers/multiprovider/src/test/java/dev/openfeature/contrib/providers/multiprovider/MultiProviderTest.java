package dev.openfeature.contrib.providers.multiprovider;

import dev.openfeature.sdk.*;
import dev.openfeature.sdk.exceptions.FlagNotFoundError;
import dev.openfeature.sdk.exceptions.GeneralError;
import dev.openfeature.sdk.providers.memory.Flag;
import dev.openfeature.sdk.providers.memory.InMemoryProvider;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MultiProviderTest {

    @BeforeEach
    void setUp() {
    }

    @Test
    public void testInit() {
        FeatureProvider provider1 = mock(FeatureProvider.class);
        FeatureProvider provider2 = mock(FeatureProvider.class);
        when(provider1.getMetadata()).thenReturn(() -> "provider1");
        when(provider2.getMetadata()).thenReturn(() -> "provider2");

        List<FeatureProvider> providers = new ArrayList<>(2);
        providers.add(provider1);
        providers.add(provider2);
        Strategy strategy = mock(Strategy.class);
        MultiProvider multiProvider = new MultiProvider(providers, strategy);

        assertNotNull(multiProvider);
        assertEquals("Multi-Provider[" + providers.get(0).getMetadata().getName() + "," +
            providers.get(1).getMetadata().getName() + "]", multiProvider.getMetadata().getName());
    }

    @Test
    public void testDuplicateProviderNames() {
        FeatureProvider provider1 = mock(FeatureProvider.class);
        FeatureProvider provider2 = mock(FeatureProvider.class);
        when(provider1.getMetadata()).thenReturn(() -> "provider");
        when(provider2.getMetadata()).thenReturn(() -> "provider");

        List<FeatureProvider> providers = new ArrayList<>(2);
        providers.add(provider1);
        providers.add(provider2);

        assertThrows(IllegalArgumentException.class, () -> new MultiProvider(providers, null));
    }

    @Test
    public void testRetrieveMetadataName() {
        // Prepare
        List<FeatureProvider> providers = new ArrayList<>();
        FeatureProvider mockProvider = mock(FeatureProvider.class);
        when(mockProvider.getMetadata()).thenReturn(() -> "MockProvider");
        providers.add(mockProvider);
        Strategy mockStrategy = mock(Strategy.class);
        MultiProvider multiProvider = new MultiProvider(providers, mockStrategy);

        // Verify
        assertEquals("Multi-Provider[MockProvider]", multiProvider.getMetadata().getName());
    }

    @SneakyThrows
    @Test
    public void testEvaluations() {
        Map<String, Flag<?>> flags1 = new HashMap<>();
        flags1.put("b1", Flag.builder().variant("true", true).defaultVariant("true").build());
        flags1.put("i1", Flag.builder().variant("v", 1).defaultVariant("v").build());
        flags1.put("d1", Flag.builder().variant("v", 1.0).defaultVariant("v").build());
        flags1.put("s1", Flag.builder().variant("v", "str1").defaultVariant("v").build());
        flags1.put("o1", Flag.builder().variant("v", new Value("v1"))
            .defaultVariant("v").build());
        InMemoryProvider provider1 = new InMemoryProvider(flags1) {
            public Metadata getMetadata() {
                return () -> "InMemoryProvider1";
            }
        };
        Map<String, Flag<?>> flags2 = new HashMap<>();
        flags2.put("b1", Flag.builder().variant("true", true).defaultVariant("false").build());
        flags2.put("i1", Flag.builder().variant("v", 2).defaultVariant("v").build());
        flags2.put("d1", Flag.builder().variant("v", 2.0).defaultVariant("v").build());
        flags2.put("s1", Flag.builder().variant("v", "str2").defaultVariant("v").build());
        flags2.put("o1", Flag.builder().variant("v", new Value("v2"))
                .defaultVariant("v").build());

        flags2.put("s2", Flag.builder().variant("v", "s2str2").defaultVariant("v").build());
        InMemoryProvider provider2 = new InMemoryProvider(flags2) {
            public Metadata getMetadata() {
                return () -> "InMemoryProvider2";
            }
        };
        List<FeatureProvider> providers = new ArrayList<>(2);
        providers.add(provider1);
        providers.add(provider2);
        MultiProvider multiProvider = new MultiProvider(providers);
        multiProvider.initialize(null);

        assertEquals(true, multiProvider.getBooleanEvaluation("b1", true, null)
            .getValue());
        assertEquals(1, multiProvider.getIntegerEvaluation("i1", 0, null)
            .getValue());
        assertEquals(1.0, multiProvider.getDoubleEvaluation("d1", 0.0, null)
            .getValue());
        assertEquals("str1", multiProvider.getStringEvaluation("s1", "", null)
            .getValue());
        assertEquals("v1", multiProvider.getObjectEvaluation("o1", null, null)
            .getValue().asString());

        assertEquals("s2str2", multiProvider.getStringEvaluation("s2", "", null)
            .getValue());
        MultiProvider finalMultiProvider1 = multiProvider;
        assertThrows(FlagNotFoundError.class, () -> {
            finalMultiProvider1.getStringEvaluation("non-existing", "", null);
        });

        multiProvider.shutdown();
        Map<String, FeatureProvider> providersMap = new HashMap<>(2);
        providersMap.put("provider1", provider1);
        providersMap.put("provider2", provider2);
        multiProvider = new MultiProvider(providers, new FirstSuccessfulStrategy(providersMap));
        multiProvider.initialize(null);

        assertEquals(true, multiProvider.getBooleanEvaluation("b1", true, null)
            .getValue());
        assertEquals(1, multiProvider.getIntegerEvaluation("i1", 0, null)
            .getValue());
        assertEquals(1.0, multiProvider.getDoubleEvaluation("d1", 0.0, null)
            .getValue());
        assertEquals("str1", multiProvider.getStringEvaluation("s1", "", null)
            .getValue());
        assertEquals("v1", multiProvider.getObjectEvaluation("o1", null, null)
            .getValue().asString());

        assertEquals("s2str2", multiProvider.getStringEvaluation("s2", "", null)
            .getValue());
        MultiProvider finalMultiProvider2 = multiProvider;
        assertThrows(GeneralError.class, () -> {
            finalMultiProvider2.getStringEvaluation("non-existing", "", null);
        });

        multiProvider.shutdown();
//        Strategy customStrategy = new BaseStrategy(providersMap) {
//            @Override
//            public <T> ProviderEvaluation<T> evaluate(Function<FeatureProvider, ProviderEvaluation<T>> providerFunction) {
//                providerFunction.
//            }
//        };
//        multiProvider = new MultiProvider(providers, customStrategy);
//        multiProvider.initialize(null);
    }
}