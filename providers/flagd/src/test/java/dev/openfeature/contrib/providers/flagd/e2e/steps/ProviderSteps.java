package dev.openfeature.contrib.providers.flagd.e2e.steps;

import static io.restassured.RestAssured.when;
import static org.assertj.core.api.Assertions.assertThat;

import dev.openfeature.contrib.providers.flagd.Config;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.FlagdProvider;
import dev.openfeature.contrib.providers.flagd.e2e.ContainerEntry;
import dev.openfeature.contrib.providers.flagd.e2e.ContainerPool;
import dev.openfeature.contrib.providers.flagd.e2e.ContainerUtil;
import dev.openfeature.contrib.providers.flagd.e2e.State;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.NoOpProvider;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.ProviderState;
import io.cucumber.java.After;
import io.cucumber.java.AfterAll;
import io.cucumber.java.BeforeAll;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.io.File;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.testcontainers.containers.ComposeContainer;

@Slf4j
public class ProviderSteps extends AbstractSteps {

    public static final int UNAVAILABLE_PORT = 9999;

    public ProviderSteps(State state) {
        super(state);
    }

    @BeforeAll
    public static void beforeAll() throws Exception {
        ContainerPool.initialize();
    }

    @AfterAll
    public static void afterAll() {
        ContainerPool.shutdown();
    }

    @After
    public void tearDown() {
        if (state.containerEntry != null) {
            if (state.client != null) {
                when().post("http://" + ContainerUtil.getLaunchpadUrl(state.containerEntry.container) + "/stop")
                        .then()
                        .statusCode(200);
            }
            ContainerPool.release(state.containerEntry);
            state.containerEntry = null;
        }
        // Replace the domain provider with a NoOp through the SDK lifecycle so the SDK
        // properly calls detachEventProvider (nulls onEmit) and shuts down the emitter
        // executor — neither of which happens when calling provider.shutdown() directly.
        if (state.providerName != null) {
            OpenFeatureAPI.getInstance().setProvider(state.providerName, new NoOpProvider());
        }
    }

    @Given("a {} flagd provider")
    public void setupProvider(String providerType) throws InterruptedException {
        state.containerEntry = ContainerPool.acquire();
        ComposeContainer container = state.containerEntry.container;

        String flagdConfig = "default";
        state.builder
                .deadline(1000)
                .keepAlive(0)
                .retryGracePeriod(2)
                .retryBackoffMs(500)
                .retryBackoffMaxMs(2000);
        boolean wait = true;

        switch (providerType) {
            case "unavailable":
                this.state.providerType = ProviderType.SOCKET;
                state.builder.port(UNAVAILABLE_PORT);
                if (State.resolverType == Config.Resolver.FILE) {
                    state.builder.offlineFlagSourcePath("not-existing");
                }
                wait = false;
                break;
            case "forbidden":
                state.builder.port(container.getServicePort("envoy", ContainerEntry.FORBIDDEN_PORT));
                wait = false;
                break;
            case "socket":
                this.state.providerType = ProviderType.SOCKET;
                String socketPath = state.containerEntry
                        .tempDir
                        .resolve("socket.sock")
                        .toAbsolutePath()
                        .toString();
                state.builder.socketPath(socketPath);
                state.builder.port(UNAVAILABLE_PORT);
                break;
            case "ssl":
                String path = "test-harness/ssl/custom-root-cert.crt";
                File file = new File(path);
                String absolutePath = file.getAbsolutePath();
                this.state.providerType = ProviderType.SSL;
                state.builder
                        .port(ContainerUtil.getPort(container, State.resolverType))
                        .tls(true)
                        .certPath(absolutePath);
                flagdConfig = "ssl";
                break;
            case "metadata":
                flagdConfig = "metadata";
                if (State.resolverType == Config.Resolver.FILE) {
                    FlagdOptions build = state.builder.build();
                    String selector = build.getSelector();
                    String replace = selector.replace("rawflags/", "");
                    state.builder
                            .port(UNAVAILABLE_PORT)
                            .offlineFlagSourcePath(new File("test-harness/flags/" + replace).getAbsolutePath());
                } else {
                    state.builder.port(ContainerUtil.getPort(container, State.resolverType));
                }
                break;
            case "syncpayload":
                flagdConfig = "sync-payload";
                state.builder.port(ContainerUtil.getPort(container, State.resolverType));
                break;
            case "stable":
                this.state.providerType = ProviderType.DEFAULT;
                if (State.resolverType == Config.Resolver.FILE) {
                    state.builder
                            .port(UNAVAILABLE_PORT)
                            .offlineFlagSourcePath(state.containerEntry
                                    .tempDir
                                    .resolve("allFlags.json")
                                    .toAbsolutePath()
                                    .toString());
                } else {
                    state.builder.port(ContainerUtil.getPort(container, State.resolverType));
                }
                break;
            default:
                throw new IllegalStateException();
        }

        // Setting TargetUri if this setting is set
        FlagdOptions tempBuild = state.builder.build();
        if (!StringUtils.isEmpty(tempBuild.getTargetUri())) {
            String replace = tempBuild.getTargetUri().replace("<port>", "" + container.getServicePort("envoy", 9211));
            state.builder.targetUri(replace);
            state.builder.port(UNAVAILABLE_PORT);
        }

        when().post("http://" + ContainerUtil.getLaunchpadUrl(container) + "/start?config={config}", flagdConfig)
                .then()
                .statusCode(200);

        Thread.sleep(50);

        FeatureProvider provider =
                new FlagdProvider(state.builder.resolverType(State.resolverType).build());
        String providerName = "Provider " + Math.random();
        OpenFeatureAPI api = OpenFeatureAPI.getInstance();

        if (wait) {
            api.setProviderAndWait(providerName, provider);
        } else {
            api.setProvider(providerName, provider);
        }
        this.state.provider = provider;
        this.state.providerName = providerName;
        this.state.client = api.getClient(providerName);
    }

    @When("the connection is lost")
    public void the_connection_is_lost() {
        when().post("http://" + ContainerUtil.getLaunchpadUrl(state.containerEntry.container) + "/stop")
                .then()
                .statusCode(200);
    }

    @When("the connection is lost for {int}s")
    public void the_connection_is_lost_for(int seconds) {
        when().post(
                        "http://" + ContainerUtil.getLaunchpadUrl(state.containerEntry.container)
                                + "/restart?seconds={seconds}",
                        seconds)
                .then()
                .statusCode(200);
    }

    @When("the flag was modified")
    public void the_flag_was_modded() {
        when().post("http://" + ContainerUtil.getLaunchpadUrl(state.containerEntry.container) + "/change")
                .then()
                .statusCode(200);
    }

    @Then("the client should be in {} state")
    public void the_client_should_be_in_fatal_state(String clientState) {
        assertThat(state.client.getProviderState()).isEqualTo(ProviderState.valueOf(clientState.toUpperCase()));
    }
}
