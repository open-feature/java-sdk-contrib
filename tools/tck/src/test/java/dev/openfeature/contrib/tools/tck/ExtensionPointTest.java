package dev.openfeature.contrib.tools.tck;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.junit.platform.engine.Constants;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.ClasspathResourceSource;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.suite.api.ConfigurationParameter;

/**
 * An adopter extends the suite by convention: features under {@code extensions/} and steps in
 * {@code openfeature.tck.extensions} are picked up with no annotations.
 */
class ExtensionPointTest {

    private static final String EXTENSION_FEATURE = ProviderTck.EXTENSIONS + "/extension-selftest.feature";

    @Test
    @DisplayName("an extension feature is discovered into the same suite and engine as the canonical set")
    void extensionsJoinTheCanonicalSuite() {
        TestPlan plan = discover(LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectClass(TckSuiteFixture.class)));

        TestIdentifier suite = only(plan, ExtensionPointTest::isTckSuite);
        TestIdentifier cucumber = only(
                plan, identifier -> "cucumber".equals(engineIdOf(identifier)) && isDescendant(plan, identifier, suite));

        List<String> resources = new ArrayList<>();
        for (TestIdentifier test : testsUnder(plan, cucumber)) {
            resourceOf(test).ifPresent(resources::add);
        }

        assertThat(resources)
                .as("extension and canonical scenarios share one Cucumber engine under one suite")
                .contains(EXTENSION_FEATURE)
                .contains(ProviderTck.FEATURES + "/errors.feature");
    }

    @Test
    @DisplayName("the extension glue package is resolved, so an adopter's steps need no registration")
    void theExtensionGluePackageIsResolved() {
        // executed, since unresolved glue only shows at run time; canonical glue omitted to avoid Docker
        SummaryGeneratingListener summary = new SummaryGeneratingListener();
        LauncherFactory.create()
                .execute(
                        LauncherDiscoveryRequestBuilder.request()
                                .selectors(DiscoverySelectors.selectClasspathResource(EXTENSION_FEATURE))
                                .configurationParameter(Constants.GLUE_PROPERTY_NAME, ProviderTck.EXTENSION_GLUE)
                                .configurationParameter(
                                        Constants.PARALLEL_EXECUTION_ENABLED_PROPERTY_NAME,
                                        ProviderTck.PARALLEL_EXECUTION_ENABLED)
                                .configurationParameter(
                                        Constants.OBJECT_FACTORY_PROPERTY_NAME, ProviderTck.OBJECT_FACTORY)
                                .build(),
                        summary);

        assertThat(summary.getSummary().getTestsSucceededCount())
                .as("the fixture scenario ran with its steps resolved from %s", ProviderTck.EXTENSION_GLUE)
                .isEqualTo(1);
        assertThat(summary.getSummary().getTotalFailureCount()).isZero();
    }

    @Test
    @DisplayName("the extension directory ships in this artifact, so the selector resolves with no adopter files")
    void theExtensionDirectoryIsShipped() {
        // a selector on a missing resource is a hard discovery error
        assertThat(getClass().getClassLoader().getResource(ProviderTck.EXTENSIONS + "/README.md"))
                .as(
                        "%s/README.md keeps the extension selector resolvable for an adopter who adds nothing",
                        ProviderTck.EXTENSIONS)
                .isNotNull();
    }

    @Test
    @DisplayName("the glue constant composes in an annotation value")
    void theGlueConstantComposesInAnAnnotationValue() {
        // VendorGlue compiling is the real evidence; annotation values must be constants
        ConfigurationParameter parameter = VendorGlue.class.getAnnotation(ConfigurationParameter.class);

        assertThat(parameter.value())
                .isEqualTo("dev.openfeature.contrib.tools.tck.internal.steps,"
                        + "openfeature.tck.extensions,com.vendor.steps");
    }

    /** An adopter who wants a third glue package writes this, and does not restate our package. */
    @ConfigurationParameter(key = Constants.GLUE_PROPERTY_NAME, value = ProviderTck.ALL_GLUE + ",com.vendor.steps")
    private static final class VendorGlue {}

    private static TestPlan discover(LauncherDiscoveryRequestBuilder request) {
        return LauncherFactory.create().discover(request.build());
    }

    private static Optional<String> resourceOf(TestIdentifier identifier) {
        Optional<TestSource> source = identifier.getSource();
        if (!source.isPresent() || !(source.get() instanceof ClasspathResourceSource)) {
            return Optional.empty();
        }
        return Optional.of(((ClasspathResourceSource) source.get()).getClasspathResourceName());
    }

    private static String engineIdOf(TestIdentifier identifier) {
        return identifier.getUniqueIdObject().getLastSegment().getType().equals("engine")
                ? identifier.getUniqueIdObject().getLastSegment().getValue()
                : null;
    }

    private static boolean isDescendant(TestPlan plan, TestIdentifier identifier, TestIdentifier ancestor) {
        Optional<TestIdentifier> parent = plan.getParent(identifier);
        while (parent.isPresent()) {
            if (parent.get().equals(ancestor)) {
                return true;
            }
            parent = plan.getParent(parent.get());
        }
        return false;
    }

    private static List<TestIdentifier> testsUnder(TestPlan plan, TestIdentifier root) {
        List<TestIdentifier> tests = new ArrayList<>();
        collectTests(plan, root, tests);
        return tests;
    }

    private static void collectTests(TestPlan plan, TestIdentifier identifier, List<TestIdentifier> into) {
        if (identifier.isTest()) {
            into.add(identifier);
        }
        for (TestIdentifier child : plan.getChildren(identifier)) {
            collectTests(plan, child, into);
        }
    }

    private static TestIdentifier only(TestPlan plan, Predicate<TestIdentifier> predicate) {
        List<TestIdentifier> matching = new ArrayList<>();
        for (TestIdentifier root : plan.getRoots()) {
            collectMatching(plan, root, predicate, matching);
        }
        assertThat(matching).hasSize(1);
        return matching.get(0);
    }

    private static void collectMatching(
            TestPlan plan, TestIdentifier identifier, Predicate<TestIdentifier> predicate, List<TestIdentifier> into) {
        if (predicate.test(identifier)) {
            into.add(identifier);
        }
        for (TestIdentifier child : plan.getChildren(identifier)) {
            collectMatching(plan, child, predicate, into);
        }
    }

    /** The suite node for {@link TckSuiteFixture}, named here rather than borrowed from the listener. */
    private static boolean isTckSuite(TestIdentifier identifier) {
        return identifier
                .getSource()
                .filter(ClassSource.class::isInstance)
                .map(source -> ((ClassSource) source).getJavaClass())
                .filter(TckSuiteFixture.class::equals)
                .isPresent();
    }
}
