package dev.openfeature.contrib.hooks.codereadiness;

import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.Hook;
import dev.openfeature.sdk.HookContext;
import dev.openfeature.sdk.ImmutableMetadata;
import dev.openfeature.sdk.exceptions.GeneralError;

import java.util.Map;
import java.util.Objects;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@Builder(builderMethodName = "", builderClassName = "Builder")
@Slf4j
public class CodeReadinessHook implements Hook {

    private static final String DEFAULT_MIN_CODE_VERSION_KEY = "minCodeVersion";
    private static final boolean DEFAULT_STRICT_VALIDATION = false;
    private static final VersionComparator DEFAULT_VERSION_COMPARATOR = new SemVerComparator();

    private final String currentVersion;

    @Builder.Default
    private final boolean strictValidation = DEFAULT_STRICT_VALIDATION;

    @Builder.Default
    private final String metadataMinVerKey = DEFAULT_MIN_CODE_VERSION_KEY;

    @Builder.Default
    private final VersionComparator comparator = DEFAULT_VERSION_COMPARATOR;

    CodeReadinessHook(String currentVersion, boolean strictValidation, String metadataMinVerKey, VersionComparator comparator) {
        this.currentVersion = Objects.requireNonNull(currentVersion, "codereadiness: currentVersion cannot be null");
        this.strictValidation = strictValidation;
        this.metadataMinVerKey = Objects.requireNonNull(metadataMinVerKey, "codereadiness: metadataMinVerKey cannot be null");
        this.comparator = Objects.requireNonNull(comparator, "codereadiness: comparator cannot be null");
    }

    public static Builder builder(String currentVersion) {
        Objects.requireNonNull(currentVersion, "codereadiness: currentVersion cannot be null");
        return new Builder().currentVersion(currentVersion);
    } 

    @Override
    public void after(HookContext ctx, FlagEvaluationDetails details, Map hints) {
        ImmutableMetadata metadata = details != null ? details.getFlagMetadata() : null;
        if (metadata == null || metadata.isEmpty()) {
            if (strictValidation) {
                throw new GeneralError(String.format("flag metadata is null for flag \"%s\"", ctx.getFlagKey()));
            }
            log.debug("flag metadata is null for flag \"{}\", skipping validation", ctx.getFlagKey());
            return;
        }
        Object minVerObj = metadata.asUnmodifiableMap().get(metadataMinVerKey);
        if (minVerObj == null) {
            if (strictValidation) {
                throw new GeneralError(String.format("key \"%s\" missing in flag's \"%s\" metadata", metadataMinVerKey, ctx.getFlagKey()));
            }
            log.debug("key \"{}\" missing in flag's \"{}\" metadata, skipping validation", metadataMinVerKey, ctx.getFlagKey());
            return;
        }
        if (!(minVerObj instanceof String)) {
            if (strictValidation) {
                throw new GeneralError(String.format("metadata \"%s\" is not a string for flag \"%s\"", metadataMinVerKey, ctx.getFlagKey()));
            }
            log.debug("metadata \"{}\" is not a string for flag \"{}\", skipping validation", metadataMinVerKey, ctx.getFlagKey());
            return;
        }
        String minCodeVersion = (String) minVerObj;
        if (minCodeVersion.isEmpty()) {
            if (strictValidation) {
                throw new GeneralError(String.format("metadata \"%s\" is empty for flag \"%s\"", metadataMinVerKey, ctx.getFlagKey()));
            }
            log.debug("metadata \"{}\" is empty for flag \"{}\", skipping validation", metadataMinVerKey, ctx.getFlagKey());
            return;
        }
        boolean isCodeReady;
        try {
            isCodeReady = comparator.compare(currentVersion, minCodeVersion);
        } catch (Exception err) {
            throw new GeneralError(
                    String.format(
                            "current version: \"%s\" required minimum version: \"%s\" check failed: %s",
                            currentVersion, minCodeVersion, err.getMessage()),
                    err);
        }
        if (!isCodeReady) {
            throw new GeneralError(String.format("current version: \"%s\" required minimum version: \"%s\" check failed", currentVersion, minCodeVersion));
        }
    }


}