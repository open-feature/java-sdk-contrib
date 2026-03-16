package dev.openfeature.contrib.tools.flagd.api.testkit;

/**
 * Enumeration of flag value types supported by the flagd evaluator.
 * The {@link #gherkinName} corresponds to the type token used in Gherkin feature files
 * (e.g., {@code a Boolean-flag with key ...}).
 */
public enum FlagType {

    BOOLEAN("Boolean"),
    STRING("String"),
    INTEGER("Integer"),
    FLOAT("Float"),
    OBJECT("Object");

    private final String gherkinName;

    FlagType(String gherkinName) {
        this.gherkinName = gherkinName;
    }

    /** Returns the Gherkin type token (e.g., {@code "Boolean"}). */
    public String getGherkinName() {
        return gherkinName;
    }

    /**
     * Resolves a {@link FlagType} from its Gherkin name.
     *
     * @param name the Gherkin type token (case-sensitive)
     * @return the matching {@link FlagType}
     * @throws IllegalArgumentException if no match is found
     */
    public static FlagType fromString(String name) {
        for (FlagType t : values()) {
            if (t.gherkinName.equals(name)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Unknown flag type: " + name);
    }
}
