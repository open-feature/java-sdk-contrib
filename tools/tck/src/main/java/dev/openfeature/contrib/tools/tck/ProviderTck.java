package dev.openfeature.contrib.tools.tck;

/**
 * The values {@link ProviderTckTest} configures Cucumber with, as compile-time constants.
 *
 * <p>Every one of these is already implied by the suite's annotations. They are named here because
 * an annotation value has to be a compile-time constant, so an adopter who adds a
 * {@code @ConfigurationParameter} of their own cannot compute one — they would otherwise have to
 * restate our package name, our resource directory or our object factory as a string literal, and a
 * literal copy of someone else's configuration is a copy that goes stale without anything failing.
 * Annotation values permit constant concatenation, so {@code ProviderTck.ALL_GLUE + ",com.vendor.steps"}
 * is legal where a method call is not.
 *
 * <p>A class of its own rather than constants on the suite. The values describe the TCK's classpath
 * conventions rather than the behaviour of a suite, and things that are not suites read them: a build
 * check, a custom launcher, a test that asserts the extension point still works. Putting them on
 * {@link ProviderTckTest} would also inherit the whole namespace into every adopter's suite class,
 * where {@code GLUE} would show up as a member of their own type.
 *
 * <p>Nothing here is a setting. Changing what the suite passes to Cucumber means changing the
 * annotations on {@link ProviderTckTest}; these constants follow that, they do not drive it.
 */
public final class ProviderTck {

    /**
     * Classpath directory holding the canonical feature files, packaged inside this JAR.
     *
     * <p>Reserved for the conformance suite. A feature file an adopter adds here does not extend the
     * canonical set, and one that collides with a canonical file name silently replaces it — see
     * {@link #EXTENSIONS}.
     *
     * <p>Named for the directory the assets have in Appendix F rather than for Cucumber's habit of
     * calling them features. Appendix F identifies a canonical feature by its path relative to the
     * asset directory — {@code gherkin/errors.feature} — and a consumer joining results from several
     * languages keys on that path, so the directory a runner reports has to be this one. Cucumber's
     * {@code classpath:} scheme in front of it is the runner's and is compared past, not stripped.
     */
    public static final String FEATURES = "gherkin";

    /**
     * Classpath directory an adopter puts their own feature files in.
     *
     * <p>Deliberately not a subdirectory of {@link #FEATURES}, and deliberately a different name.
     * The same directory name in two classpath roots is scanned additively, but the same directory
     * <em>and</em> file name is not: one root wins and the other file is never read, with no warning.
     * An adopter who dropped {@code gherkin/errors.feature} beside ours would therefore replace a
     * canonical file with their own and watch the suite go green having run theirs — the worst
     * outcome available to a conformance suite. Two distinct directories, {@code gherkin/} and
     * {@code extensions/}, make that collision impossible to reach by accident.
     *
     * <p>{@code extensions/} is also the prefix Appendix F reserves for exactly this, so the path a
     * runner reports for an adopter's scenario is one any consumer can tell apart from a canonical
     * one without knowing anything about this implementation.
     *
     * <p>Shipped in this JAR containing only a README, because
     * {@link org.junit.platform.suite.api.SelectClasspathResource} on a resource that exists nowhere
     * on the classpath is a discovery error rather than an empty selection. Cucumber ignores files
     * that are not {@code .feature}, so the README costs nothing.
     */
    public static final String EXTENSIONS = "extensions";

    /** Package holding the canonical step definitions. */
    public static final String GLUE = "dev.openfeature.contrib.tools.tck.steps";

    /**
     * Package an adopter puts their own step definitions in.
     *
     * <p>Outside this artifact's package namespace on purpose: it is the adopter's package, not
     * ours, and it lives in their source tree. A glue package that does not exist is tolerated
     * silently by Cucumber, so an adopter who writes no extensions pays nothing for it being on the
     * glue path.
     */
    public static final String EXTENSION_GLUE = "openfeature.tck.extensions";

    /**
     * The glue path the suite runs with: the canonical steps and the extension package.
     *
     * <p>Concatenate to add more, as {@code ProviderTck.ALL_GLUE + ",com.vendor.steps"}. Note that
     * an adopter adding a package this way has to keep the canonical one, or every canonical step
     * becomes undefined.
     */
    public static final String ALL_GLUE = GLUE + "," + EXTENSION_GLUE;

    /**
     * The Cucumber plugins the suite registers.
     *
     * <p>Spelled out rather than derived from {@code ConformanceReportPlugin.class.getName()},
     * which is not a compile-time constant and so cannot appear in an annotation value.
     */
    public static final String PLUGINS = "summary,dev.openfeature.contrib.tools.tck.ConformanceReportPlugin";

    /**
     * Whether scenarios may run in parallel: never.
     *
     * <p>Backend state is global to the suite, so concurrent scenarios corrupt each other. The suite
     * pins this where it overrides a consuming module's {@code junit-platform.properties}.
     */
    public static final String PARALLEL_EXECUTION_ENABLED = "false";

    /** Execution mode for the scenarios of one feature, matching {@link #PARALLEL_EXECUTION_ENABLED}. */
    public static final String FEATURE_EXECUTION_MODE = "same_thread";

    /** Object factory that injects {@link TckState} into every step class. */
    public static final String OBJECT_FACTORY = "io.cucumber.picocontainer.PicoFactory";

    private ProviderTck() {}
}
