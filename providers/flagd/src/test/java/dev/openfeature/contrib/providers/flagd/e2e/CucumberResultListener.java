package dev.openfeature.contrib.providers.flagd.e2e;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.reporting.ReportEntry;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

/**
 * Captures the full lifecycle of a JUnit Platform test execution, tracking start, finish, and skip
 * events for every node in the test plan (both containers and tests). Results are later replayed as
 * JUnit Jupiter {@link org.junit.jupiter.api.DynamicTest} instances to expose the Cucumber scenario
 * tree in IDEs.
 */
@Slf4j
class CucumberResultListener implements TestExecutionListener {

    private final Set<String> started = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<String, TestExecutionResult> results = new ConcurrentHashMap<>();
    private final Map<String, String> skipped = new ConcurrentHashMap<>();

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        log.debug("Cucumber execution started");
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        log.debug(
                "Cucumber execution finished — started={}, finished={}, skipped={}",
                started.size(),
                results.size(),
                skipped.size());
    }

    @Override
    public void executionStarted(TestIdentifier id) {
        log.debug("  START  {}", id.getDisplayName());
        started.add(id.getUniqueId());
    }

    @Override
    public void executionFinished(TestIdentifier id, TestExecutionResult result) {
        results.put(id.getUniqueId(), result);
        if (result.getStatus() == TestExecutionResult.Status.FAILED) {
            log.debug(
                    "  FAIL   {} — {}",
                    id.getDisplayName(),
                    result.getThrowable().map(Throwable::getMessage).orElse("(no message)"));
        } else {
            log.debug("  {}   {}", result.getStatus(), id.getDisplayName());
        }
    }

    @Override
    public void executionSkipped(TestIdentifier id, String reason) {
        skipped.put(id.getUniqueId(), reason);
        log.debug("  SKIP   {} — {}", id.getDisplayName(), reason);
    }

    @Override
    public void dynamicTestRegistered(TestIdentifier id) {
        log.debug("  DYN    {}", id.getDisplayName());
    }

    @Override
    public void reportingEntryPublished(TestIdentifier id, ReportEntry entry) {
        log.debug("  REPORT {} — {}", id.getDisplayName(), entry);
    }

    /** Whether the node with the given unique ID had {@code executionStarted} called. */
    boolean wasStarted(String uniqueId) {
        return started.contains(uniqueId);
    }

    /** Whether the node was skipped before starting. */
    boolean wasSkipped(String uniqueId) {
        return skipped.containsKey(uniqueId);
    }

    /** The skip reason for a skipped node, or {@code null} if not skipped. */
    String getSkipReason(String uniqueId) {
        return skipped.get(uniqueId);
    }

    /** Whether a finished result was recorded for the given node. */
    boolean hasResult(String uniqueId) {
        return results.containsKey(uniqueId);
    }

    /**
     * The recorded {@link TestExecutionResult}, or {@code null} if the node never finished.
     * Use {@link #hasResult} to distinguish "finished with success" from "never finished".
     */
    TestExecutionResult getResult(String uniqueId) {
        return results.get(uniqueId);
    }
}
