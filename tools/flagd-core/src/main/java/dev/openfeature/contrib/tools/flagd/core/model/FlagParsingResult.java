package dev.openfeature.contrib.tools.flagd.core.model;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Map;
import lombok.Getter;

/**
 * The result of the parsing of a json string containing feature flag definitions.
 */
@Getter
@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP"},
        justification = "Feature flag comes as a Json configuration, hence they must be exposed")
public class FlagParsingResult {
    private final Map<String, FeatureFlag> flags;
    private final Map<String, Object> flagSetMetadata;

    /**
     * Construct a parsing result.
     *
     * @param flags           the parsed flags
     * @param flagSetMetadata the flag set metadata
     */
    public FlagParsingResult(Map<String, FeatureFlag> flags, Map<String, Object> flagSetMetadata) {
        this.flags = flags;
        this.flagSetMetadata = flagSetMetadata;
    }
}
