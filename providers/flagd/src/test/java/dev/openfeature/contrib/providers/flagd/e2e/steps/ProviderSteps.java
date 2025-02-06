package dev.openfeature.contrib.providers.flagd.e2e.steps;

import static io.restassured.RestAssured.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import dev.openfeature.contrib.providers.flagd.Config;
import dev.openfeature.contrib.providers.flagd.FlagdProvider;
import dev.openfeature.contrib.providers.flagd.e2e.FlagdContainer;
import dev.openfeature.contrib.providers.flagd.e2e.State;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.OpenFeatureAPI;
import io.cucumber.java.After;
import io.cucumber.java.AfterAll;
import io.cucumber.java.Before;
import io.cucumber.java.BeforeAll;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.parallel.Isolated;
import org.testcontainers.containers.BindMode;
import org.testcontainers.shaded.org.apache.commons.io.FileUtils;

@Isolated()
@Slf4j
public class ProviderSteps extends AbstractSteps {

    public static final int UNAVAILABLE_PORT = 9999;
    static FlagdContainer container;

    static Path sharedTempDir;

    public ProviderSteps(State state) {
        super(state);
    }

    @BeforeAll
    public static void beforeAll() throws IOException {
        State.resolverType = Config.Resolver.RPC;
        sharedTempDir = Files.createDirectories(
                Paths.get("tmp/" + RandomStringUtils.randomAlphanumeric(8).toLowerCase() + "/"));
        container = new FlagdContainer()
                .withFileSystemBind(sharedTempDir.toAbsolutePath().toString(), "/tmp", BindMode.READ_WRITE);
    }

    @AfterAll
    public static void afterAll() throws IOException {
        container.stop();
        FileUtils.deleteDirectory(sharedTempDir.toFile());
    }

    @Before
    public void before() throws IOException {
        if (!container.isRunning()) {
            container.start();
        }
    }

    @After
    public void tearDown() throws InterruptedException {
        if (state.client != null) {
            when().post("http://" + container.getLaunchpadUrl() + "/stop")
                    .then()
                    .statusCode(200);
        }
        OpenFeatureAPI.getInstance().shutdown();
    }

    @Given("a {} flagd provider")
    public void setupProvider(String providerType) throws IOException, InterruptedException {
        String flagdConfig = "default";
        state.builder.deadline(1000).keepAlive(0).retryGracePeriod(2);
        boolean wait = true;
        File flags = new File("test-harness/flags");
        ObjectMapper objectMapper = new ObjectMapper();
        Object merged = new Object();
        for (File listFile : Objects.requireNonNull(flags.listFiles())) {
            ObjectReader updater = objectMapper.readerForUpdating(merged);
            merged = updater.readValue(listFile, Object.class);
        }
        Path offlinePath = Files.createTempFile("flags", ".json");
        objectMapper.writeValue(offlinePath.toFile(), merged);
        switch (providerType) {
            case "unavailable":
                this.state.providerType = ProviderType.SOCKET;
                state.builder.port(UNAVAILABLE_PORT);
                if (State.resolverType == Config.Resolver.FILE) {

                    state.builder.offlineFlagSourcePath("not-existing");
                }
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
                        .port(container.getPort(State.resolverType))
                        .tls(true)
                        .certPath(absolutePath);
                flagdConfig = "ssl";
                break;

            default:
                this.state.providerType = ProviderType.DEFAULT;
                if (State.resolverType == Config.Resolver.FILE) {

                    state.builder
                            .port(UNAVAILABLE_PORT)
                            .offlineFlagSourcePath(offlinePath.toAbsolutePath().toString());
                } else {
                    state.builder.port(container.getPort(State.resolverType));
                }
                break;
        }
        when().post("http://" + container.getLaunchpadUrl() + "/start?config={config}", flagdConfig)
                .then()
                .statusCode(200);

        // giving flagd a little time to start
        Thread.sleep(100);
        FeatureProvider provider =
                new FlagdProvider(state.builder.resolverType(State.resolverType).build());

        OpenFeatureAPI api = OpenFeatureAPI.getInstance();
        String providerName = providerType + Math.random();
        if (wait) {
            api.setProviderAndWait(providerName, provider);
        } else {
            api.setProvider(providerName, provider);
        }
        log.info("provider name: {}", providerName);
        this.state.client = api.getClient(providerName);
    }

    @When("the connection is lost")
    public void the_connection_is_lost() throws InterruptedException {
        when().post("http://" + container.getLaunchpadUrl() + "/stop").then().statusCode(200);
    }

    @When("the connection is lost for {int}s")
    public void the_connection_is_lost_for(int seconds) throws InterruptedException {
        when().post("http://" + container.getLaunchpadUrl() + "/restart?seconds={seconds}", seconds)
                .then()
                .statusCode(200);
    }

    @When("the flag was modified")
    public void the_flag_was_modded() throws InterruptedException {

        when().post("http://" + container.getLaunchpadUrl() + "/change").then().statusCode(200);

        // we might be too fast in the execution
        Thread.sleep(100);
    }
}
