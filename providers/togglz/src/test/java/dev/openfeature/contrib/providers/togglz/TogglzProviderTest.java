package dev.openfeature.contrib.providers.togglz;

import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.exceptions.FlagNotFoundError;
import dev.openfeature.sdk.exceptions.ProviderNotReadyError;
import dev.openfeature.sdk.exceptions.TypeMismatchError;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.togglz.core.context.StaticFeatureManagerProvider;
import org.togglz.core.manager.FeatureManager;
import org.togglz.core.manager.FeatureManagerBuilder;
import org.togglz.core.repository.FeatureState;
import org.togglz.core.repository.StateRepository;
import org.togglz.core.repository.mem.InMemoryStateRepository;
import org.togglz.core.user.NoOpUserProvider;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TogglzProviderTest {

    private FeatureProvider featureProvider;

    private Client client;

    @BeforeEach
    void setUp() throws Exception {
        StateRepository stateRepository = new InMemoryStateRepository();
        stateRepository.setFeatureState(new FeatureState(TestFeatures.FEATURE_ONE, true));
        stateRepository.setFeatureState(new FeatureState(TestFeatures.FEATURE_TWO, false));

        FeatureManager featureManager = new FeatureManagerBuilder()
            .featureEnums(TestFeatures.class)
            .stateRepository(stateRepository)
            .userProvider(new NoOpUserProvider())
            .build();
        StaticFeatureManagerProvider.setFeatureManager(featureManager);

        OpenFeatureAPI api = OpenFeatureAPI.getInstance();
        featureProvider = new TogglzProvider(Arrays.asList(TestFeatures.values()));
        api.setProviderAndWait(featureProvider);
        client = api.getClient();
    }

    @Test
    void getBooleanEvaluation() {
        assertEquals(true, featureProvider.getBooleanEvaluation(TestFeatures.FEATURE_ONE.name(), false, new ImmutableContext()).getValue());
        assertEquals(true, client.getBooleanValue(TestFeatures.FEATURE_ONE.name(), false));
        assertEquals(false, featureProvider.getBooleanEvaluation(TestFeatures.FEATURE_TWO.name(), false, new ImmutableContext()).getValue());
        assertEquals(false, client.getBooleanValue(TestFeatures.FEATURE_TWO.name(), false));
    }

    @Test
    void notFound() {
        assertThrows(FlagNotFoundError.class, () -> {
            featureProvider.getBooleanEvaluation("not-found-flag", false, new ImmutableContext());
        });
    }

    @Test
    void typeMismatch() {
        assertThrows(TypeMismatchError.class, () -> {
            featureProvider.getStringEvaluation(TestFeatures.FEATURE_ONE.name(), "default_value", new ImmutableContext());
        });
    }

    @SneakyThrows
    @Test
    void shouldThrowIfNotInitialized() {
        TogglzProvider togglzProvider = new TogglzProvider(Arrays.asList(TestFeatures.values()));

        // ErrorCode.PROVIDER_NOT_READY should be returned when evaluated via the client
        assertThrows(ProviderNotReadyError.class, ()-> togglzProvider.getBooleanEvaluation("fail_not_initialized", false, new ImmutableContext()));
    }
}