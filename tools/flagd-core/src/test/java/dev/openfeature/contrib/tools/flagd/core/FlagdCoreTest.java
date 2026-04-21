package dev.openfeature.contrib.tools.flagd.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import dev.openfeature.contrib.tools.flagd.api.FlagStoreException;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Reason;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FlagdCoreTest {

    private FlagdCore flagdCore;
    private String flagsConfig;

    @BeforeEach
    void setUp() throws FlagStoreException, IOException {
        flagsConfig = readResource("flags/test-flags.json");
        flagdCore = new FlagdCore();
        flagdCore.setFlags(flagsConfig);
    }

    private String readResource(String path) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new IOException("Resource not found: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void resolveBooleanValue_returnsCorrectValue() {
        ProviderEvaluation<Boolean> result = flagdCore.resolveBooleanValue("boolFlag", false, new ImmutableContext());

        assertThat(result.getValue()).isTrue();
        assertThat(result.getVariant()).isEqualTo("on");
        assertThat(result.getReason()).isEqualTo(Reason.STATIC.toString());
    }

    @Test
    void resolveStringValue_returnsCorrectValue() {
        ProviderEvaluation<String> result =
                flagdCore.resolveStringValue("stringFlag", "default", new ImmutableContext());

        assertThat(result.getValue()).isEqualTo("hello");
        assertThat(result.getVariant()).isEqualTo("greeting");
        assertThat(result.getReason()).isEqualTo(Reason.STATIC.toString());
    }

    @Test
    void resolveIntegerValue_returnsCorrectValue() {
        ProviderEvaluation<Integer> result = flagdCore.resolveIntegerValue("intFlag", -1, new ImmutableContext());

        assertThat(result.getValue()).isEqualTo(1);
        assertThat(result.getVariant()).isEqualTo("one");
    }

    @Test
    void resolveDoubleValue_returnsCorrectValue() {
        ProviderEvaluation<Double> result = flagdCore.resolveDoubleValue("doubleFlag", -1.0, new ImmutableContext());

        assertThat(result.getValue()).isEqualTo(3.14);
        assertThat(result.getVariant()).isEqualTo("pi");
    }

    @Test
    void resolveBooleanValue_flagNotFound_returnsError() {
        ProviderEvaluation<Boolean> result =
                flagdCore.resolveBooleanValue("missingFlag", false, new ImmutableContext());

        assertThat(result.getErrorCode()).isNotNull();
        assertThat(result.getErrorMessage()).contains("not found");
    }

    @Test
    void resolveBooleanValue_disabledFlag_returnsError() {
        ProviderEvaluation<Boolean> result =
                flagdCore.resolveBooleanValue("disabledFlag", false, new ImmutableContext());

        assertThat(result.getErrorCode()).isNotNull();
        assertThat(result.getErrorMessage()).contains("disabled");
    }

    @Test
    void getFlagSetMetadata_returnsMetadata() {
        assertThat(flagdCore.getFlagSetMetadata()).containsEntry("version", "1.0.0");
    }

    @Test
    void setFlagsAndGetChangedKeys_returnsChangedKeys() throws FlagStoreException {
        var changedKeys = flagdCore.setFlagsAndGetChangedKeys(flagsConfig);
        assertThat(changedKeys).isEmpty(); // No changes, same config

        // Change the config
        String newConfig = flagsConfig.replace("\"on\": true", "\"on\": false");
        changedKeys = flagdCore.setFlagsAndGetChangedKeys(newConfig);
        assertThat(changedKeys).contains("boolFlag");
    }

    @Test
    void setFlagsAndGetChangedKeys_detectsRemovedFlags() throws FlagStoreException {
        // Given: initial config has boolFlag
        assertThat(flagdCore
                        .resolveBooleanValue("boolFlag", false, new ImmutableContext())
                        .getValue())
                .isTrue();

        // When: update with config that removes boolFlag (keeps only stringFlag)
        String configWithoutBoolFlag = "{"
                + "\"$schema\": \"https://flagd.dev/schema/v0/flags.json\","
                + "\"flags\": {"
                + "  \"stringFlag\": {"
                + "    \"state\": \"ENABLED\","
                + "    \"defaultVariant\": \"greeting\","
                + "    \"variants\": { \"greeting\": \"hello\" }"
                + "  }"
                + "},"
                + "\"metadata\": { \"version\": \"1.0.0\" }"
                + "}";
        var changedKeys = flagdCore.setFlagsAndGetChangedKeys(configWithoutBoolFlag);

        // Then: boolFlag should be in the changed keys (it was removed)
        assertThat(changedKeys).contains("boolFlag");
    }

    @Test
    void resolveBooleanValue_flagWithNullMetadata_doesNotThrowNPE() {
        String configWithNullMetadata = "{"
                + "\"$schema\": \"https://flagd.dev/schema/v0/flags.json\","
                + "\"flags\": {"
                + "  \"nullMetadataFlag\": {"
                + "    \"state\": \"ENABLED\","
                + "    \"defaultVariant\": \"on\","
                + "    \"variants\": { \"on\": true }"
                + "  }"
                + "}"
                + "}";

        assertThatNoException().isThrownBy(() -> {
            flagdCore.setFlags(configWithNullMetadata);
            ProviderEvaluation<Boolean> result =
                    flagdCore.resolveBooleanValue("nullMetadataFlag", false, new ImmutableContext());
            assertThat(result.getFlagMetadata()).isNotNull();
        });
    }
}
