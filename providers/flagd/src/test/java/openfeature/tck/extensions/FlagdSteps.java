package openfeature.tck.extensions;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.resolver.rpc.cache.CacheType;
import dev.openfeature.contrib.providers.flagd.tck.FlagdTckHarness;
import dev.openfeature.contrib.tools.tck.ProviderTck;
import dev.openfeature.contrib.tools.tck.ProviderTckHarness;
import dev.openfeature.contrib.tools.tck.TckState;
import dev.openfeature.sdk.MutableContext;
import dev.openfeature.sdk.Value;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Shared steps for the flagd extension scenarios: the resolver-tag check, provider options and the
 * launchpad endpoints the canonical {@code BackendControl} does not cover.
 *
 * <p>The features port flagd-testbed scenarios the TCK does not ask. They keep the testbed's
 * wording wherever it does not collide with a TCK step. It collides on the provider: the TCK's
 * {@code a {} provider} also matches {@code a stable flagd provider}, so a scenario records its
 * options with {@code an option ...} and then uses the TCK's {@code Given a stable provider}.
 */
public class FlagdSteps {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

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

    private final TckState state;
    private final FlagdTckHarness flagd;

    /** The launchpad configuration this scenario switched to, if any. */
    private String servedConfiguration;

    public FlagdSteps(TckState state, ProviderTckHarness harness) {
        this.state = state;
        this.flagd = (FlagdTckHarness) harness;
    }

    /** The resolver tags; each suite includes its own with {@code @TckIncludeTags}. */
    private static final List<String> RESOLVER_TAGS = Arrays.asList("@rpc", "@in-process", "@file");

    /**
     * Fails an extension scenario that carries none of {@code @rpc}, {@code @in-process} and
     * {@code @file}, on the scenario or its feature. The resolver tag is the include, as in the
     * flagd-testbed, so one without it would otherwise be skipped silently in every suite.
     *
     * <p>Runs before the TCK's gate (order 0), which applies the suites' {@code @TckIncludeTags} and
     * would skip such a scenario first. Canonical scenarios carry no resolver tag and are exempt.
     *
     * @param scenario the scenario about to run
     */
    @Before(order = -1)
    public void requireResolverTag(Scenario scenario) {
        if (!scenario.getUri().toString().contains("/" + ProviderTck.EXTENSIONS + "/")) {
            return;
        }
        Collection<String> tags = scenario.getSourceTagNames();
        if (RESOLVER_TAGS.stream().noneMatch(tags::contains)) {
            throw new IllegalStateException("Extension scenario '" + scenario.getName() + "' (" + scenario.getUri()
                    + ") carries none of " + RESOLVER_TAGS + "; tag it or its feature");
        }
    }

    /**
     * Leaves nothing behind for a canonical scenario that follows: no customization, and flagd back
     * on the {@code default} configuration. The TCK's own preparation may only reset flag state,
     * which would keep a scenario's {@code ssl} or {@code metadata} configuration running.
     */
    @After
    public void restoreDefaults() {
        flagd.resetCustomizations();
        if (servedConfiguration != null && !"default".equals(servedConfiguration)) {
            launchpad("/start?config=default");
        }
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

    /**
     * Restarts flagd on one of the launchpad's flag configurations ({@code default},
     * {@code metadata}, {@code ssl}, {@code sync-payload}).
     *
     * <p>Comes before {@code Given a stable provider}, so the provider connects to it. The TCK's
     * {@code the connection is restored} returns to {@code default}.
     *
     * @param configuration the launchpad configuration name
     */
    @Given("the flagd backend serves the {string} configuration")
    public void theBackendServes(String configuration) {
        launchpad("/start?config=" + configuration);
        servedConfiguration = configuration;
    }

    /**
     * Stops flagd and starts it again after the given delay, without waiting for it.
     *
     * @param seconds how long flagd stays down
     */
    @When("the connection is lost for {int}s")
    public void theConnectionIsLostFor(int seconds) {
        launchpad("/restart?seconds=" + seconds);
    }

    /**
     * Adds typed entries to the evaluation context.
     *
     * @param table rows with {@code key}, {@code type} and {@code value} columns
     */
    @Given("a context with the following keys:")
    public void aContextWithTheFollowingKeys(DataTable table) throws IOException {
        Map<String, Value> entries = new HashMap<>(state.context.asMap());
        for (Map<String, String> row : table.asMaps(String.class, String.class)) {
            entries.put(row.get("key"), Value.objectToValue(convert(row.get("value"), row.get("type"))));
        }
        state.context = new MutableContext(state.context.getTargetingKey(), entries);
    }

    /**
     * Asserts that no event of the given kind was recorded during the scenario.
     *
     * @param eventType the event kind
     */
    @Then("the {} event handler should not have been executed")
    public void theEventHandlerShouldNotHaveBeenExecuted(String eventType) {
        assertThat(state.events.stream().anyMatch(event -> event.type().equals(eventType)))
                .as("no %s event should have fired", eventType)
                .isFalse();
    }

    private void launchpad(String path) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(flagd.launchpadUrl() + path))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        try {
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).as("launchpad POST %s", path).isEqualTo(200);
        } catch (IOException e) {
            throw new IllegalStateException("launchpad POST " + path + " failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted during launchpad POST " + path, e);
        }
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
