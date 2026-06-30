package dev.openfeature.contrib.hooks.codereadiness;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.HookContext;
import dev.openfeature.sdk.ImmutableMetadata;
import dev.openfeature.sdk.exceptions.GeneralError;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CodeReadinessHookTest {

    private final HookContext hookContext = mock(HookContext.class);

    @BeforeEach
    void setUp() {
        when(hookContext.getFlagKey()).thenReturn("testFlag");
    }

    @Test
    @DisplayName("Should pass when current version is equal or greater than required metadata version")
    void testValidVersionPasses() {
        CodeReadinessHook hook = CodeReadinessHook.builder("1.5.0").build();
        FlagEvaluationDetails details = createDetailsWithMetadata("minCodeVersion", "1.2.0");

        assertThatCode(() -> hook.after(hookContext, details, Collections.emptyMap()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should throw GeneralError when current version is less than required metadata version")
    void testInvalidVersionThrowsGeneralError() {
        CodeReadinessHook hook = CodeReadinessHook.builder("1.0.0").build();
        FlagEvaluationDetails details = createDetailsWithMetadata("minCodeVersion", "1.2.0");

        assertThatThrownBy(() -> hook.after(hookContext, details, Collections.emptyMap()))
                .isInstanceOf(GeneralError.class)
                .hasMessage("current version: \"1.0.0\" required minimum version: \"1.2.0\" check failed");
    }

    @Test
    @DisplayName("Should ignore missing metadata when strictValidation is false")
    void testMissingMetadataIgnoredWhenValidationNotRequired() {
        CodeReadinessHook hook = CodeReadinessHook.builder("1.0.0")
                .strictValidation(false)
                .build();

        assertThatCode(() -> hook.after(hookContext, null, Collections.emptyMap()))
                .doesNotThrowAnyException();

        FlagEvaluationDetails emptyMetadataDetails = FlagEvaluationDetails.builder()
                .flagMetadata(ImmutableMetadata.builder().build())
                .build();
        assertThatCode(() -> hook.after(hookContext, emptyMetadataDetails, Collections.emptyMap()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should throw GeneralError when metadata is missing and strictValidation is true")
    void testMissingMetadataThrowsWhenStrictValidation() {
        CodeReadinessHook hook = CodeReadinessHook.builder("1.0.0")
                .strictValidation(true)
                .build();

        assertThatThrownBy(() -> hook.after(hookContext, null, Collections.emptyMap()))
                .isInstanceOf(GeneralError.class)
                .hasMessage("flag metadata is null for flag \"testFlag\"");

        FlagEvaluationDetails emptyMetadataDetails = FlagEvaluationDetails.builder()
                .flagMetadata(ImmutableMetadata.builder().build())
                .build();
        assertThatThrownBy(() -> hook.after(hookContext, emptyMetadataDetails, Collections.emptyMap()))
                .isInstanceOf(GeneralError.class)
                .hasMessage("flag metadata is null for flag \"testFlag\"");
    }

    @Test
    @DisplayName("Should ignore missing minCodeVersion key when strictValidation is false")
    void testMissingKeyIgnoredWhenStrictValidationNotRequired() {
        CodeReadinessHook hook = CodeReadinessHook.builder("1.0.0")
                .strictValidation(false)
                .build();
        FlagEvaluationDetails details = createDetailsWithMetadata("otherKey", "1.0.0");

        assertThatCode(() -> hook.after(hookContext, details, Collections.emptyMap()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should throw GeneralError when minCodeVersion key is missing and strictValidation is true")
    void testMissingKeyThrowsWhenStrictValidation() {
        CodeReadinessHook hook = CodeReadinessHook.builder("1.0.0")
                .strictValidation(true)
                .build();
        FlagEvaluationDetails details = createDetailsWithMetadata("otherKey", "1.0.0");

        assertThatThrownBy(() -> hook.after(hookContext, details, Collections.emptyMap()))
                .isInstanceOf(GeneralError.class)
                .hasMessage("key \"minCodeVersion\" missing in flag's \"testFlag\" metadata");
    }

    @Test
    @DisplayName("Should use custom metadataMinVerKey when specified")
    void testCustomMetadataMinVerKey() {
        CodeReadinessHook hook = CodeReadinessHook.builder("2.0.0")
                .metadataMinVerKey("customMinVersion")
                .strictValidation(true)
                .build();
        FlagEvaluationDetails details = createDetailsWithMetadata("customMinVersion", "1.5.0");

        assertThatCode(() -> hook.after(hookContext, details, Collections.emptyMap()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should throw GeneralError when metadata value is not a string and strictValidation is true")
    void testNonStringMetadataValueThrows() {
        CodeReadinessHook hook = CodeReadinessHook.builder("1.0.0").strictValidation(true).build();
        FlagEvaluationDetails details = createDetailsWithMetadata("minCodeVersion", true);

        assertThatThrownBy(() -> hook.after(hookContext, details, Collections.emptyMap()))
                .isInstanceOf(GeneralError.class)
                .hasMessage("metadata \"minCodeVersion\" is not a string for flag \"testFlag\"");
    }

    @Test
    @DisplayName("Should ignore non string metadata value when strictValidation is false")
    void testNonStringMetadataValueIgnores() {
        CodeReadinessHook hook = CodeReadinessHook.builder("1.0.0").strictValidation(false).build();
        FlagEvaluationDetails details = createDetailsWithMetadata("minCodeVersion", true);

        assertThatCode(() -> hook.after(hookContext, details, Collections.emptyMap()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should ignore empty minCodeVersion value when strictValidation is false")
    void testEmptyVersionStringIgnoredWhenStrictValidationNotRequired() {
        CodeReadinessHook hook = CodeReadinessHook.builder("1.0.0")
                .strictValidation(false)
                .build();
        FlagEvaluationDetails details = createDetailsWithMetadata("minCodeVersion", "");

        assertThatCode(() -> hook.after(hookContext, details, Collections.emptyMap()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should throw GeneralError when minCodeVersion string is empty and strictValidation is true")
    void testEmptyVersionStringThrowsWhenStrictValidation() {
        CodeReadinessHook hook = CodeReadinessHook.builder("1.0.0")
                .strictValidation(true)
                .build();
        FlagEvaluationDetails details = createDetailsWithMetadata("minCodeVersion", "");

        assertThatThrownBy(() -> hook.after(hookContext, details, Collections.emptyMap()))
                .isInstanceOf(GeneralError.class)
                .hasMessage("metadata \"minCodeVersion\" is empty for flag \"testFlag\"");
    }

    @Test
    @DisplayName("Should throw NullPointerException when building hook with null arguments")
    void testNullArgumentsThrowNpeAtBuildTime() {
        assertThatThrownBy(() -> CodeReadinessHook.builder(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("codereadiness: currentVersion cannot be null");

        assertThatThrownBy(() -> CodeReadinessHook.builder("1.0.0").comparator(null).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessage("codereadiness: comparator cannot be null");

        assertThatThrownBy(() -> CodeReadinessHook.builder("1.0.0").metadataMinVerKey(null).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessage("codereadiness: metadataMinVerKey cannot be null");
    }

    @Test
    @DisplayName("Should use custom comparator when configured")
    void testCustomComparator() throws Exception {
        VersionComparator mockComparator = mock(VersionComparator.class);
        when(mockComparator.compare(anyString(), anyString())).thenReturn(false);

        CodeReadinessHook hook = CodeReadinessHook.builder("10.0.0")
                .comparator(mockComparator)
                .build();
        FlagEvaluationDetails details = createDetailsWithMetadata("minCodeVersion", "1.0.0");

        assertThatThrownBy(() -> hook.after(hookContext, details, Collections.emptyMap()))
                .isInstanceOf(GeneralError.class)
                .hasMessage("current version: \"10.0.0\" required minimum version: \"1.0.0\" check failed");
    }

    @Test
    @DisplayName("Should wrap exception thrown by comparator into GeneralError")
    void testComparatorExceptionWrappedInGeneralError() throws Exception {
        VersionComparator mockComparator = mock(VersionComparator.class);
        when(mockComparator.compare(anyString(), anyString())).thenThrow(new RuntimeException("comparator error"));

        CodeReadinessHook hook = CodeReadinessHook.builder("1.0.0")
                .comparator(mockComparator)
                .build();
        FlagEvaluationDetails details = createDetailsWithMetadata("minCodeVersion", "1.0.0");

        assertThatThrownBy(() -> hook.after(hookContext, details, Collections.emptyMap()))
                .isInstanceOf(GeneralError.class)
                .hasMessage("current version: \"1.0.0\" required minimum version: \"1.0.0\" check failed: comparator error")
                .hasCauseInstanceOf(RuntimeException.class);
    }

    private FlagEvaluationDetails createDetailsWithMetadata(String key, Object value) {
        ImmutableMetadata.ImmutableMetadataBuilder builder = ImmutableMetadata.builder();
        if (value instanceof Boolean) {
            builder.addBoolean(key, (Boolean) value);
        } else {
            builder.addString(key, value.toString());
        }
        return FlagEvaluationDetails.builder()
                .flagMetadata(builder.build())
                .build();
    }
}
