package dev.openfeature.contrib.providers.flagd.e2e.steps;

import static io.restassured.RestAssured.when;
import static org.assertj.core.api.Assertions.assertThat;

import dev.openfeature.contrib.providers.flagd.Config;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.FlagdProvider;
import dev.openfeature.contrib.providers.flagd.e2e.ContainerUtil;
import dev.openfeature.contrib.providers.flagd.e2e.State;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.ProviderState;
import io.cucumber.java.After;
import io.cucumber.java.AfterAll;
import io.cucumber.java.BeforeAll;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;

@Slf4j
public class ProviderSteps extends AbstractSteps {

    public static final int UNAVAILABLE_PORT = 9999;
    public static final int FORBIDDEN_PORT = 9212;
    static ComposeContainer container;

    static Path sharedTempDir;

    public ProviderSteps(State state) {
        super(state);
    }

    @BeforeAll
    public static void beforeAll() throws IOException {
        sharedTempDir = Files.createDirectories(
                Paths.get("tmp/" + RandomStringUtils.randomAlphanumeric(8).toLowerCase() + "/"));
        container = new ComposeContainer(new File("test-harness/docker-compose.yaml"))
                .withEnv("FLAGS_DIR", sharedTempDir.toAbsolutePath().toString())
                .withExposedService("flagd", 8013, Wait.forListeningPort())
                .withExposedService("flagd", 8015, Wait.forListeningPort())
                .withExposedService("flagd", 8080, Wait.forListeningPort())
                .withExposedService("envoy", 9211, Wait.forListeningPort())
                .withExposedService("envoy", FORBIDDEN_PORT, Wait.forListeningPort())
                .withStartupTimeout(Duration.ofSeconds(45));
        container.start();
    }

    @AfterAll
    public static void afterAll() throws IOException {
        container.stop();
        FileUtils.deleteDirectory(sharedTempDir.toFile());
    }

    @After
    public void tearDown() {
        if (state.client != null) {
            when().post("http://" + ContainerUtil.getLaunchpadUrl(container) + "/stop")
                    .then()
                    .statusCode(200);
        }
        OpenFeatureAPI.getInstance().shutdown();
    }

    @Given("a {} flagd provider")
    public void setupProvider(String providerType) throws InterruptedException {
        String flagdConfig = "default";
        state.builder.deadline(1000).keepAlive(0).retryGracePeriod(2);
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
                state.builder.port(container.getServicePort("envoy", FORBIDDEN_PORT));
                wait = false;
                break;
            case "socket":
                this.state.providerType = ProviderType.SOCKET;
                String socketPath =
                        sharedTempDir.resolve("socket.sock").toAbsolutePath().toString();
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
                            .offlineFlagSourcePath(sharedTempDir
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
        this.state.client = api.getClient(providerName);
    }

    @When("the connection is lost")
    public void the_connection_is_lost() {
        when().post("http://" + ContainerUtil.getLaunchpadUrl(container) + "/stop")
                .then()
                .statusCode(200);
    }

    @When("the connection is lost for {int}s")
    public void the_connection_is_lost_for(int seconds) {
        when().post("http://" + ContainerUtil.getLaunchpadUrl(container) + "/restart?seconds={seconds}", seconds)
                .then()
                .statusCode(200);
    }

    @When("the flag was modified")
    public void the_flag_was_modded() {
        when().post("http://" + ContainerUtil.getLaunchpadUrl(container) + "/change")
                .then()
                .statusCode(200);
    }

    @Then("the client should be in {} state")
    public void the_client_should_be_in_fatal_state(String clientState) {
        assertThat(state.client.getProviderState()).isEqualTo(ProviderState.valueOf(clientState.toUpperCase()));
    }
}
