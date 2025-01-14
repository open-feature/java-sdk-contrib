package dev.openfeature.contrib.providers.flagd.resolver.process.model;

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
public class ParsingResult {
    private final Map<String, FeatureFlag> flags;
    private final Map<String, Object> flagSetMetadata;

    public ParsingResult(Map<String, FeatureFlag> flags, Map<String, Object> flagSetMetadata) {
        this.flags = flags;
        this.flagSetMetadata = flagSetMetadata;
    }
}
