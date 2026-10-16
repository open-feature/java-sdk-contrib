package dev.openfeature.contrib.tools.tck;

/**
 * The Cucumber configuration values {@link ProviderTckTest} uses, as compile-time constants for
 * composing, e.g. {@code ProviderTck.ALL_GLUE + ",com.vendor.steps"}.
 */
public final class ProviderTck {

    /** Classpath directory of the canonical feature files; reserved, use {@link #EXTENSIONS}. */
    public static final String FEATURES = "gherkin";

    /**
     * Classpath directory for adopter feature files; separate from {@link #FEATURES} so they cannot
     * shadow canonical files.
     */
    public static final String EXTENSIONS = "extensions";

    /** Package holding the canonical step definitions. */
    public static final String GLUE = "dev.openfeature.contrib.tools.tck.internal.steps";

    /** Package for adopter step definitions; may be absent. */
    public static final String EXTENSION_GLUE = "openfeature.tck.extensions";

    /** The glue path: canonical steps plus the extension package. */
    public static final String ALL_GLUE = GLUE + "," + EXTENSION_GLUE;

    /** The Cucumber plugins the suite registers. */
    public static final String PLUGINS = "summary";

    /** Parallel execution is disabled; backend state is global to the suite. */
    public static final String PARALLEL_EXECUTION_ENABLED = "false";

    /** Execution mode for the scenarios of one feature, matching {@link #PARALLEL_EXECUTION_ENABLED}. */
    public static final String FEATURE_EXECUTION_MODE = "same_thread";

    /** Object factory that injects {@link TckState} into every step class. */
    public static final String OBJECT_FACTORY = "io.cucumber.picocontainer.PicoFactory";

    private ProviderTck() {}
}
