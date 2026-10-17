package dev.openfeature.contrib.tools.tck;

/**
 * The suite's Cucumber configuration. Only the three constants an extension composes with are
 * public; the lifecycle lives with the glue it serves.
 */
public final class ProviderTck {

    // compile-time constants, so the suite classes can use them in annotations

    /** Classpath directory of the canonical feature files; reserved, use {@link #EXTENSIONS}. */
    static final String FEATURES = "gherkin";

    /**
     * Classpath directory for adopter feature files; separate from {@link #FEATURES} so they cannot
     * shadow canonical files.
     */
    public static final String EXTENSIONS = "extensions";

    /** Package holding the canonical step definitions. */
    static final String GLUE = "dev.openfeature.contrib.tools.tck.internal.steps";

    /** Package for adopter step definitions; may be absent. */
    public static final String EXTENSION_GLUE = "openfeature.tck.extensions";

    /** The glue path: canonical steps plus the extension package. */
    public static final String ALL_GLUE = GLUE + "," + EXTENSION_GLUE;

    /** The Cucumber plugins the suite registers. */
    static final String PLUGINS = "summary";

    /**
     * Scenarios of one feature stay on one thread, so enabling parallel execution gives
     * feature-level concurrency rather than scenario-level.
     */
    static final String FEATURE_EXECUTION_MODE = "same_thread";

    /** Object factory that injects {@link TckState} into every step class. */
    static final String OBJECT_FACTORY =
            "dev.openfeature.contrib.tools.tck.internal.steps.TckObjectFactory";

    private ProviderTck() {
        // constants only
    }
}
