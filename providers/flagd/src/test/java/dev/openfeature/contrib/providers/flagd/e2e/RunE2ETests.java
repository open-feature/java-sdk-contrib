package dev.openfeature.contrib.providers.flagd.e2e;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.OBJECT_FACTORY_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PARALLEL_EXECUTION_ENABLED_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectDirectory;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.platform.launcher.EngineFilter;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TagFilter;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

/**
 * Runs all three resolver modes (RPC, in-process, file) concurrently via three
 * {@code @TestFactory} methods. Each factory launches a full Cucumber engine run for its
 * resolver, captures every scenario result via {@link CucumberResultListener}, then returns
 * a {@code Stream<DynamicNode>} that mirrors the TestPlan hierarchy — giving IDEs a
 * fully-expandable tree (Feature → Scenario) with accurate pass/fail/skip per scenario.
 *
 * <p>With {@code @Execution(CONCURRENT)} on each factory method and
 * {@code junit.jupiter.execution.parallel.enabled=true} in {@code junit-platform.properties},
 * all three Cucumber runs execute simultaneously, so wall-clock time ≈ max(RPC, IN_PROCESS, FILE).
 *
 * <p>Each factory method ({@link #rpc()}, {@link #inProcess()}, {@link #file()}) can also be
 * run individually from an IDE for targeted single-resolver debugging.
 *
 * <p>Run via {@code -Pe2e} from the repo root:
 * <pre>./mvnw -pl providers/flagd -Pe2e test</pre>
 */
public class RunE2ETests {

    private static final String STEPS = "dev.openfeature.contrib.providers.flagd.e2e.steps";

    @TestFactory
    @Execution(ExecutionMode.CONCURRENT)
    Stream<DynamicNode> rpc() {
        return resolverTests(STEPS + ".resolver.rpc", "rpc", "unixsocket", "deprecated");
    }

    @TestFactory
    @Execution(ExecutionMode.CONCURRENT)
    Stream<DynamicNode> inProcess() {
        // targetURI scenarios are excluded: the retryBackoffMaxMs that controls initial-connection
        // throttle also controls post-disconnect reconnect backoff, so they cannot be tuned
        // independently. Under parallel load the first getMetadata() call times out (envoy
        // upstream not yet ready), the throttle fires for retryBackoffMaxMs, and the retry arrives
        // after the waitForInitialization deadline. Tracked in flagd issue #1584 — once
        // getMetadata() is removed, these scenarios can be re-enabled.
        return resolverTests(STEPS + ".resolver.inprocess", "in-process", "unixsocket", "targetURI", "deprecated");
    }

    @TestFactory
    @Execution(ExecutionMode.CONCURRENT)
    Stream<DynamicNode> file() {
        return resolverTests(
                STEPS + ".resolver.file",
                "file",
                "unixsocket",
                "targetURI",
                "reconnect",
                "customCert",
                "events",
                "contextEnrichment",
                "deprecated");
    }

    private Stream<DynamicNode> resolverTests(String resolverGlue, String includeTag, String... excludeTags) {
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectDirectory("test-harness/gherkin"))
                .filters(
                        EngineFilter.includeEngines("cucumber"),
                        TagFilter.includeTags(includeTag),
                        TagFilter.excludeTags(excludeTags))
                .configurationParameter(GLUE_PROPERTY_NAME, STEPS + "," + resolverGlue)
                .configurationParameter(PLUGIN_PROPERTY_NAME, "summary")
                .configurationParameter(OBJECT_FACTORY_PROPERTY_NAME, "io.cucumber.picocontainer.PicoFactory")
                .configurationParameter(PARALLEL_EXECUTION_ENABLED_PROPERTY_NAME, "true")
                .configurationParameter("cucumber.execution.parallel.config.strategy", "dynamic")
                .configurationParameter("cucumber.execution.parallel.config.dynamic.factor", "1")
                .configurationParameter("cucumber.execution.exclusive-resources.env-var.read-write", "ENV_VARS")
                .configurationParameter("cucumber.execution.exclusive-resources.grace.read-write", "CONTAINER_RESTART")
                .build();

        Launcher launcher = LauncherFactory.create();
        TestPlan testPlan = launcher.discover(request);

        // Run the full Cucumber suite synchronously, capturing all lifecycle events.
        // Internal Cucumber scenario-parallelism (cucumber.execution.parallel.enabled) still applies.
        CucumberResultListener listener = new CucumberResultListener();
        launcher.execute(request, listener);

        // Build a DynamicNode tree mirroring the discovered TestPlan (engine → feature → scenario).
        return testPlan.getRoots().stream()
                .flatMap(root -> testPlan.getChildren(root).stream())
                .flatMap(node -> buildNodes(testPlan, node, listener));
    }

    private Stream<DynamicNode> buildNodes(TestPlan plan, TestIdentifier id, CucumberResultListener listener) {
        if (id.isTest()) {
            String uid = id.getUniqueId();
            return Stream.of(DynamicTest.dynamicTest(id.getDisplayName(), () -> {
                if (listener.wasSkipped(uid)) {
                    Assumptions.assumeTrue(false, listener.getSkipReason(uid));
                    return;
                }
                if (!listener.wasStarted(uid)) {
                    Assumptions.assumeTrue(false, "Scenario was discovered but not executed");
                    return;
                }
                if (!listener.hasResult(uid)) {
                    throw new AssertionError("Scenario started but did not complete: " + uid);
                }
                switch (listener.getResult(uid).getStatus()) {
                    case FAILED:
                        Throwable t = listener.getResult(uid)
                                .getThrowable()
                                .orElse(new AssertionError("Test failed: " + uid));
                        if (t instanceof AssertionError) throw (AssertionError) t;
                        if (t instanceof RuntimeException) throw (RuntimeException) t;
                        throw new AssertionError(t);
                    case ABORTED:
                        Assumptions.assumeTrue(
                                false,
                                listener.getResult(uid)
                                        .getThrowable()
                                        .map(Throwable::getMessage)
                                        .orElse("aborted"));
                        break;
                    default:
                        break;
                }
            }));
        }
        Set<TestIdentifier> children = plan.getChildren(id);
        if (children.isEmpty()) return Stream.empty();
        List<DynamicNode> childNodes = children.stream()
                .flatMap(child -> buildNodes(plan, child, listener))
                .collect(Collectors.toList());
        if (childNodes.isEmpty()) return Stream.empty();
        return Stream.of(DynamicContainer.dynamicContainer(id.getDisplayName(), childNodes.stream()));
    }
}
