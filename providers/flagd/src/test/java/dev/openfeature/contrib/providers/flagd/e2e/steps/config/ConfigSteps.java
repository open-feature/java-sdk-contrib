package dev.openfeature.contrib.providers.flagd.e2e.steps.config;

import dev.openfeature.contrib.providers.flagd.Config;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.resolver.grpc.cache.CacheType;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

public class ConfigSteps {

    FlagdOptions.FlagdOptionsBuilder builder = FlagdOptions.builder();
    FlagdOptions options;

    @When("we initialize a config")
    public void we_initialize_a_config() {
        options = builder.build();
    }

    @When("we initialize a config for {string}")
    public void we_initialize_a_config_for(String string) {
        switch (string.toLowerCase()) {
            case "in-process":
                options = builder.resolverType(Config.Resolver.IN_PROCESS).build();
                break;
            case "rpc":
                options = builder.resolverType(Config.Resolver.RPC).build();
                break;
            default:
                throw new RuntimeException("Unknown resolver type: " + string);
        }
    }

    @When("we have an option {string} of type {string} with value {string}")
    public void we_have_an_option_of_type_with_value(String option, String type, String value) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Object converted = convert(value, type);
        Method method = Arrays.stream(builder.getClass().getMethods())
                .filter(method1 -> method1.getName().equals(option))
                .findFirst()
                .orElseThrow(RuntimeException::new);
        method.invoke(builder, converted);
    }


    Map<String, String> envVarsSet = new HashMap<>();

    @When("we have an environment variable {string} with value {string}")
    public void we_have_an_environment_variable_with_value(String varName, String value) throws IllegalAccessException, NoSuchFieldException {
        String getenv = System.getenv(varName);
        envVarsSet.put(varName, getenv);
        EnvironmentVariableUtils.set(varName, value);
    }

    private Object convert(String value, String type) throws ClassNotFoundException {
        if (Objects.equals(value, "null")) return null;
        switch (type) {
            case "Boolean":
                return Boolean.parseBoolean(value);
            case "String":
                return value;
            case "Integer":
                return Integer.parseInt(value);
            case "Long":
                return Long.parseLong(value);
            case "ResolverType":
                switch (value.toLowerCase()) {
                    case "in-process":
                        return Config.Resolver.IN_PROCESS;
                    case "rpc":
                        return Config.Resolver.RPC;
                    default:
                        throw new RuntimeException("Unknown resolver type: " + value);
                }
            case "CacheType":
                return CacheType.valueOf(value.toUpperCase()).getValue();
        }
        throw new RuntimeException("Unknown config type: " + type);
    }

    @Then("the option {string} of type {string} should have the value {string}")
    public void the_option_of_type_should_have_the_value(String option, String type, String value) throws Throwable {
        Object convert = convert(value, type);

        assertThat(options).hasFieldOrPropertyWithValue(option, convert);

        // Resetting env vars
        for (Map.Entry<String, String> envVar : envVarsSet.entrySet()) {
            if (envVar.getValue() == null) {
                EnvironmentVariableUtils.clear(envVar.getKey());
            } else {
                EnvironmentVariableUtils.set(envVar.getKey(), envVar.getValue());
            }
        }
    }
}
