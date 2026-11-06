package dev.openfeature.contrib.tools.tck;

/**
 * The flag a scenario is exercising: key, declared type (which selects the typed evaluation call),
 * and code default.
 */
public final class FlagUnderTest {

    private final String key;
    private final String type;
    private final Object defaultValue;

    /**
     * Creates a flag under test.
     *
     * @param key the flag key
     * @param type the declared type, one of {@code Boolean}, {@code String}, {@code Integer},
     *     {@code Float} or {@code Object}
     * @param defaultValue the code default passed to the evaluation call
     */
    public FlagUnderTest(String key, String type, Object defaultValue) {
        this.key = key;
        this.type = type;
        this.defaultValue = defaultValue;
    }

    /**
     * Returns the flag key.
     *
     * @return the flag key
     */
    public String key() {
        return key;
    }

    /**
     * Returns the declared flag type.
     *
     * @return the declared flag type
     */
    public String type() {
        return type;
    }

    /**
     * Returns the code default passed to the evaluation call.
     *
     * @return the code default
     */
    public Object defaultValue() {
        return defaultValue;
    }
}
