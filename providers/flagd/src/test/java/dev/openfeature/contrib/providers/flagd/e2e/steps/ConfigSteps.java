package dev.openfeature.contrib.providers.flagd.e2e.steps;

import static org.assertj.core.api.Assertions.assertThat;

import dev.openfeature.contrib.providers.flagd.Config;
import dev.openfeature.contrib.providers.flagd.e2e.State;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigSteps extends AbstractSteps {
    /**
     * Not all properties are correctly implemented, hence that we need to ignore them till this is
     * fixed
     */
    public static final List<String> IGNORED_FOR_NOW = new ArrayList<String>() {
        {
            add("offlinePollIntervalMs");
            add("retryBackoffMaxMs");
        }
    };

    private static final Logger LOG = LoggerFactory.getLogger(ConfigSteps.class);

    public ConfigSteps(State state) {
        super(state);
    }

    @When("a config was initialized")
    public void we_initialize_a_config() {
        state.options = state.builder.build();
    }

    @When("a config was initialized for {string}")
    public void we_initialize_a_config_for(String string) {
        switch (string.toLowerCase()) {
            case "in-process":
                state.options =
                        state.builder.resolverType(Config.Resolver.IN_PROCESS).build();
                break;
            case "rpc":
                state.options = state.builder.resolverType(Config.Resolver.RPC).build();
                break;
            default:
                throw new RuntimeException("Unknown resolver type: " + string);
        }
    }

    @Given("an option {string} of type {string} with value {string}")
    public void we_have_an_option_of_type_with_value(String option, String type, String value)
            throws Throwable {
        if (IGNORED_FOR_NOW.contains(option)) {
            LOG.error("option '{}' is not supported", option);
            return;
        }

        Object converted = Utils.convert(value, type);
        Method method = Arrays.stream(state.builder.getClass().getMethods())
                .filter(method1 -> method1.getName().equals(mapOptionNames(option)))
                .findFirst()
                .orElseThrow(RuntimeException::new);
        method.invoke(state.builder, converted);
    }

    Map<String, String> envVarsSet = new HashMap<>();

    @Given("an environment variable {string} with value {string}")
    public void we_have_an_environment_variable_with_value(String varName, String value)
            throws IllegalAccessException, NoSuchFieldException {
        String getenv = System.getenv(varName);
        envVarsSet.put(varName, getenv);
        EnvironmentVariableUtils.set(varName, value);
    }

    @Then("the option {string} of type {string} should have the value {string}")
    public void the_option_of_type_should_have_the_value(String option, String type, String value) throws Throwable {
        Object convert = Utils.convert(value, type);

        if (IGNORED_FOR_NOW.contains(option)) {
            LOG.error("option '{}' is not supported", option);
            return;
        }

        option = mapOptionNames(option);

        assertThat(state.options).hasFieldOrPropertyWithValue(option, convert);

        // Resetting env vars
        for (Map.Entry<String, String> envVar : envVarsSet.entrySet()) {
            if (envVar.getValue() == null) {
                EnvironmentVariableUtils.clear(envVar.getKey());
            } else {
                EnvironmentVariableUtils.set(envVar.getKey(), envVar.getValue());
            }
        }
    }

    private static String mapOptionNames(String option) {
        Map<String, String> propertyMapper = new HashMap<>();
        propertyMapper.put("resolver", "resolverType");
        propertyMapper.put("deadlineMs", "deadline");
        propertyMapper.put("keepAliveTime", "keepAlive");
        propertyMapper.put("retryBackoffMaxMs", "keepAlive");
        propertyMapper.put("cache", "cacheType");

        if (propertyMapper.get(option) != null) {
            option = propertyMapper.get(option);
        }
        return option;
    }
}
