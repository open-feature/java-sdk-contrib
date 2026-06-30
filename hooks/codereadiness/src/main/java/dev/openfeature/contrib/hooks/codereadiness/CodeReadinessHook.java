package dev.openfeature.contrib.hooks.codereadiness;

import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.Hook;
import dev.openfeature.sdk.HookContext;
import dev.openfeature.sdk.ImmutableMetadata;
import dev.openfeature.sdk.exceptions.GeneralError;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

/**
 * Hook for controlling feature flag evaluation based on the application code version.
 */
@Slf4j
public final class CodeReadinessHook implements Hook {

    private static final String DEFAULT_MIN_CODE_VERSION_KEY = "minCodeVersion";
    private static final boolean DEFAULT_STRICT_VALIDATION = false;

    private final String currentVersion;
    private final boolean strictValidation;
    private final String metadataMinVerKey;
    private final VersionComparator<Object> comparator;
    private final Object parsedCurrentVersion;

    /**
     * Builder for {@link CodeReadinessHook}.
     */
    public static class Builder {
        String currentVersion;
        boolean strictValidation = DEFAULT_STRICT_VALIDATION;
        String metadataMinVerKey = DEFAULT_MIN_CODE_VERSION_KEY;
        VersionComparator<?> comparator = new SemVerComparator();
    }

    @lombok.Builder(builderMethodName = "", builderClassName = "Builder")
    CodeReadinessHook(
            String currentVersion,
            boolean strictValidation,
            String metadataMinVerKey,
            VersionComparator<?> comparator) {
        this.currentVersion = Objects.requireNonNull(currentVersion, "codereadiness: currentVersion cannot be null");
        this.strictValidation = strictValidation;
        this.metadataMinVerKey =
                Objects.requireNonNull(metadataMinVerKey, "codereadiness: metadataMinVerKey cannot be null");
        Objects.requireNonNull(comparator, "codereadiness: comparator cannot be null");
        this.comparator = (VersionComparator<Object>) comparator;
        try {
            this.parsedCurrentVersion = this.comparator.parse(currentVersion);
        } catch (Exception err) {
            throw new GeneralError(
                    String.format(
                            "current version: \"%s\" initialization failed: %s", currentVersion, err.getMessage()),
                    err);
        }
    }

    public static Builder builder(String currentVersion) {
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
        String minCodeVersion = metadata.getString(metadataMinVerKey);
        if (minCodeVersion == null || minCodeVersion.isEmpty()) {
            if (strictValidation) {
                throw new GeneralError(String.format(
                        "key \"%s\" missing or empty in flag's \"%s\" metadata", metadataMinVerKey, ctx.getFlagKey()));
            }
            log.debug(
                    "key \"{}\" missing or empty in flag's \"{}\", skipping validation",
                    metadataMinVerKey,
                    ctx.getFlagKey());
            return;
        }
        boolean isCodeReady;
        try {
            Object parsedMinVersion = comparator.parse(minCodeVersion);
            isCodeReady = comparator.compare(this.parsedCurrentVersion, parsedMinVersion);
        } catch (Exception err) {
            if (strictValidation) {
                throw new GeneralError(
                        String.format(
                                "current version: \"%s\" required minimum version: \"%s\" check failed: %s",
                                currentVersion, minCodeVersion, err.getMessage()),
                        err);
            }
            log.debug(String.format("invalid version values for flag \"%s\", skipping validation", ctx.getFlagKey()));
            return;
        }
        if (!isCodeReady) {
            throw new GeneralError(String.format(
                    "current version: \"%s\" required minimum version: \"%s\" check failed",
                    currentVersion, minCodeVersion));
        }
    }
}
