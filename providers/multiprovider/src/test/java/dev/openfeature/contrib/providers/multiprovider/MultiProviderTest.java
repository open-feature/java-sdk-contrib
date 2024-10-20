package dev.openfeature.contrib.providers.multiprovider;

import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.ProviderEvaluation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MultiProviderTest {

    @BeforeEach
    void setUp() {
    }

    // Initialize MultiProvider with a valid list of FeatureProviders and a strategy
    @Test
    public void testInit() {
        List<FeatureProvider> providers = new ArrayList<>(2);
        providers.add(mock(FeatureProvider.class));
        providers.add(mock(FeatureProvider.class));
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
        when(provider1.getMetadata().getName()).thenReturn("provider");
        when(provider2.getMetadata().getName()).thenReturn("provider");

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

    @Test
    public void testEvaluateBoolean() {

        // TODO test also with InMemoryProviders

//        List<FeatureProvider> providers = new ArrayList<>();
//        FeatureProvider mockProvider1 = mock(FeatureProvider.class);
//        when(mockProvider1.getBooleanEvaluation("key1", true, null)).thenReturn(ProviderEvaluation.builder().value(true).build());
//        FeatureProvider mockProvider2 = mock(FeatureProvider.class);
//        when(mockProvider2.getBooleanEvaluation("key1", true, null)).thenReturn(ProviderEvaluation.of(false));
//        providers.add(mockProvider1);
//        providers.add(mockProvider2);
//        Strategy mockStrategy = mock(Strategy.class);
//        when(mockStrategy.evaluate(any())).thenReturn(ProviderEvaluation.of(true));
//        MultiProvider multiProvider = new MultiProvider(providers, mockStrategy);
//
//        assertTrue(multiProvider.getBooleanEvaluation("key1", true, null).getValue());
    }
}