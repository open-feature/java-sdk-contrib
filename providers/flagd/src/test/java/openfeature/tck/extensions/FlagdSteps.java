package openfeature.tck.extensions;


import com.fasterxml.jackson.databind.ObjectMapper;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.resolver.rpc.cache.CacheType;
import dev.openfeature.contrib.providers.flagd.tck.FlagdTckHarness;
import dev.openfeature.contrib.tools.tck.ProviderTckHarness;
import dev.openfeature.sdk.Value;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Shared steps for the flagd extension scenarios: the provider options the testbed's features set.
 *
 * <p>Everything else these features once needed from here is the TCK's now -- serving a named
 * configuration, a timed outage, a context table and the negative event assertion were about
 * nothing flagd-specific, and two of them were the control API's own operations behind a
 * flagd-shaped step.
 *
 * <p>The features port flagd-testbed scenarios the TCK does not ask. They keep the testbed's
 * wording wherever it does not collide with a TCK step. It collides on the provider: the TCK's
 * {@code a {} provider} also matches {@code a stable flagd provider}, so a scenario records its
 * options with {@code an option ...} and then uses the TCK's {@code Given a stable provider}.
 */
public class FlagdSteps {

    /** Option names the testbed spells differently from {@link FlagdOptions.FlagdOptionsBuilder}. */
    private static final Map<String, String> OPTION_NAMES;

    static {
        Map<String, String> names = new HashMap<>();
        names.put("resolver", "resolverType");
        names.put("deadlineMs", "deadline");
        names.put("keepAliveTime", "keepAlive");
        names.put("cache", "cacheType");
        OPTION_NAMES = Collections.unmodifiableMap(names);
    }

    private final FlagdTckHarness flagd;

    public FlagdSteps(ProviderTckHarness harness) {
        this.flagd = (FlagdTckHarness) harness;
    }

    /**
     * Leaves no customization behind for a scenario that follows. Getting flagd off a scenario's own
     * configuration is the TCK's business now: {@code BackendControl} records the one a scenario
     * served and takes the next scenario back to this suite's.
     */
    @After
    public void restoreDefaults() {
        flagd.resetCustomizations();
    }

    /**
     * Sets an option on the next provider this scenario creates, by the testbed's option name.
     *
     * @param option the option name, as config.feature spells it
     * @param type the value's type
     * @param value the value
     */
    @Given("an option {string} of type {string} with value {string}")
    public void anOption(String option, String type, String value) throws IOException {
        Object converted = convert(value, type);
        String name = OPTION_NAMES.getOrDefault(option, option);
        Method setter = Arrays.stream(FlagdOptions.FlagdOptionsBuilder.class.getMethods())
                .filter(method -> method.getName().equals(name) && method.getParameterCount() == 1)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("FlagdOptions has no option '" + name + "'"));
        flagd.customize(builder -> {
            try {
                setter.invoke(builder, converted);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot set option '" + name + "' to " + converted, e);
            }
        });
    }

    /** The testbed's value conversion, for the types the extension features use. */
    static Object convert(String value, String type) throws IOException {
        if ("null".equals(value) && !"String".equals(type)) {
            return null;
        }
        switch (type) {
            case "Boolean":
                return Boolean.parseBoolean(value);
            case "String":
                return value;
            case "Integer":
                try {
                    return Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    return Long.parseLong(value);
                }
            case "Long":
                return Long.parseLong(value);
            case "Float":
                return Double.parseDouble(value);
            case "CacheType":
                return CacheType.valueOf(value.toUpperCase()).getValue();
            case "StringList":
                return value.isEmpty()
                        ? Collections.emptyList()
                        : Arrays.stream(value.split(",")).map(String::trim).collect(Collectors.toList());
            case "Object":
                return Value.objectToValue(new ObjectMapper().readValue(value, Object.class));
            default:
                throw new IllegalArgumentException("Unknown value type '" + type + "'");
        }
    }
}
