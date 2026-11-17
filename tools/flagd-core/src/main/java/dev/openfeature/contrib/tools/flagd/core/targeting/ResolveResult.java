package dev.openfeature.contrib.tools.flagd.core.targeting;

import java.util.Collections;
import java.util.Map;
import lombok.Getter;

/**
 * Result of the terminal {@code resolve} operation. No JSON input decodes to this type, so evaluation
 * context data cannot forge one.
 */
@Getter
public final class ResolveResult {
    private final String variant;
    /** Overrides the evaluation reason; {@code null} keeps {@code TARGETING_MATCH}. */
    private final String reason;
    /** Rule metadata, merged over flag-set and flag metadata. */
    private final Map<String, Object> metadata;

    ResolveResult(String variant, String reason, Map<String, Object> metadata) {
        this.variant = variant;
        this.reason = reason;
        this.metadata = Collections.unmodifiableMap(metadata);
    }
}
