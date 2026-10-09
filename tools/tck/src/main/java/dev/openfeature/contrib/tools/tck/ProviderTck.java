package dev.openfeature.contrib.tools.tck;

/**
 * The values {@link ProviderTckTest} configures Cucumber with, as compile-time constants.
 *
 * <p>Named here so that an adopter adding a {@code @ConfigurationParameter} of their own composes
 * from them — {@code ProviderTck.ALL_GLUE + ",com.vendor.steps"} — rather than restating this
 * configuration as a string literal that nothing would keep in step. Annotation values must be
 * compile-time constants, so constant concatenation is legal where a method call is not.
 *
 * <p>Nothing here is a setting. Changing what the suite passes to Cucumber means changing the
 * annotations on {@link ProviderTckTest}; these constants follow that, they do not drive it.
 */
public final class ProviderTck {

    /**
     * Classpath directory holding the canonical feature files, packaged inside this JAR.
     *
     * <p>Reserved for the conformance suite: a feature file an adopter adds here does not extend the
     * canonical set, and one colliding with a canonical file name silently replaces it — see
     * {@link #EXTENSIONS}. Named for the directory the assets have in Appendix F, because a consumer
     * joining results across languages keys on that path.
     */
    public static final String FEATURES = "gherkin";

    /**
     * Classpath directory an adopter puts their own feature files in.
     *
     * <p>Deliberately not a subdirectory of {@link #FEATURES}, and deliberately a different name.
     * The same directory <em>and</em> file name in two classpath roots is not scanned additively:
     * one root wins silently, so an adopter who dropped {@code gherkin/errors.feature} beside ours
     * would replace a canonical file and watch the suite go green having run theirs. Two distinct
     * directories make that unreachable by accident, and {@code extensions/} is the prefix
     * Appendix F reserves.
     *
     * <p>Shipped in this JAR containing only a README, because
     * {@link org.junit.platform.suite.api.SelectClasspathResource} on a resource that exists nowhere
     * on the classpath is a discovery error rather than an empty selection.
     */
    public static final String EXTENSIONS = "extensions";

    /** Package holding the canonical step definitions. */
    public static final String GLUE = "dev.openfeature.contrib.tools.tck.steps";

    /**
     * Package an adopter puts their own step definitions in.
     *
     * <p>Outside this artifact's package namespace on purpose: it is the adopter's package and
     * lives in their source tree. Cucumber tolerates a glue package that does not exist, so an
     * adopter who writes no extensions pays nothing for it being on the glue path.
     */
    public static final String EXTENSION_GLUE = "openfeature.tck.extensions";

    /**
     * The glue path the suite runs with: the canonical steps and the extension package.
     *
     * <p>Concatenate to add more, as {@code ProviderTck.ALL_GLUE + ",com.vendor.steps"}; dropping
     * the canonical package leaves every canonical step undefined.
     */
    public static final String ALL_GLUE = GLUE + "," + EXTENSION_GLUE;

    /**
     * The Cucumber plugins the suite registers.
     *
     * <p>Just Cucumber's own summary. Turning a run into a machine-readable conformance report is a
     * separate concern that registers its own plugin here.
     */
    public static final String PLUGINS = "summary";

    /**
     * Whether scenarios may run in parallel: never.
     *
     * <p>Backend state is global to the suite, so concurrent scenarios corrupt each other. Pinned on
     * the suite, where it overrides a consuming module's {@code junit-platform.properties}.
     */
    public static final String PARALLEL_EXECUTION_ENABLED = "false";

    /** Execution mode for the scenarios of one feature, matching {@link #PARALLEL_EXECUTION_ENABLED}. */
    public static final String FEATURE_EXECUTION_MODE = "same_thread";

    /** Object factory that injects {@link TckState} into every step class. */
    public static final String OBJECT_FACTORY = "io.cucumber.picocontainer.PicoFactory";

    private ProviderTck() {}
}
