package dev.openfeature.contrib.tools.flagd.api.testkit;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.OBJECT_FACTORY_PROPERTY_NAME;

import dev.openfeature.contrib.tools.flagd.api.Evaluator;
import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

/**
 * Abstract JUnit Platform Suite class for flagd-api-testkit compliance tests.
 *
 * <p>Provides all Cucumber runner configuration. The {@link EvaluatorFactory} is discovered
 * via Java SPI — consumers register their concrete subclass in a
 * {@code META-INF/services/dev.openfeature.contrib.tools.flagd.api.testkit.EvaluatorFactory}
 * file and only need to implement {@link #create(String)}.
 *
 * <p>Example — the only class a consumer needs to write:
 *
 * <pre>{@code
 * public class MyEvaluatorTest extends AbstractEvaluatorTest {
 *
 *     @Override
 *     public Evaluator create(String flagsJson) throws Exception {
 *         MyEvaluator evaluator = new MyEvaluator();
 *         evaluator.setFlags(flagsJson);
 *         return evaluator;
 *     }
 * }
 * }</pre>
 *
 * <p>Plus a file at
 * {@code src/test/resources/META-INF/services/dev.openfeature.contrib.tools.flagd.api.testkit.EvaluatorFactory}
 * containing the fully-qualified class name of the concrete test class.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "dev.openfeature.contrib.tools.flagd.api.testkit")
@ConfigurationParameter(key = OBJECT_FACTORY_PROPERTY_NAME, value = "io.cucumber.picocontainer.PicoFactory")
public abstract class AbstractEvaluatorTest implements EvaluatorFactory {

    /**
     * Creates and returns a fully configured {@link Evaluator} loaded with the provided flag JSON.
     *
     * @param flagsJson the testkit flag configuration in flagd JSON format
     * @return a configured evaluator ready for testing
     */
    @Override
    public abstract Evaluator create(String flagsJson) throws Exception;
}
