package dev.openfeature.contrib.providers.flagd.e2e.steps;

import dev.openfeature.contrib.providers.flagd.FlagdProvider;
import dev.openfeature.contrib.providers.flagd.e2e.FlagdContainer;
import dev.openfeature.contrib.providers.flagd.e2e.State;
import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.OpenFeatureAPI;
import io.cucumber.java.After;
import io.cucumber.java.AfterAll;
import io.cucumber.java.Before;
import io.cucumber.java.BeforeAll;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.parallel.Isolated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.shaded.org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

@Isolated()
public class ProviderSteps extends AbstractSteps {

    private static final Logger LOG = LoggerFactory.getLogger(ProviderSteps.class);

    public static final int UNAVAILABLE_PORT = 9999;
    static Map<ProviderType, FlagdContainer> containers = new HashMap<>();

    static Path sharedTempDir;

    static {
        try {
            sharedTempDir = Files.createDirectories(
                    Paths.get("tmp/" + RandomStringUtils.randomAlphanumeric(8).toLowerCase() + "/"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public ProviderSteps(State state) {
        super(state);
    }

    @BeforeAll
    public static void beforeAll() throws IOException {
        containers.put(ProviderType.DEFAULT, new FlagdContainer());
        containers.put(ProviderType.SSL, new FlagdContainer("ssl"));
        containers.put(ProviderType.SOCKET, new FlagdContainer("socket")
                .withFileSystemBind(sharedTempDir.toAbsolutePath().toString(), "/tmp", BindMode.READ_WRITE));

    }

    @AfterAll
    public static void afterAll() throws IOException {

        containers.forEach((name, container) -> container.stop());
        FileUtils.deleteDirectory(sharedTempDir.toFile());
    }

    @Before
    public void before() {
        containers.values().stream().filter(containers -> !containers.isRunning())
                .forEach(FlagdContainer::start);
    }
    @After
    public void tearDown() {
        OpenFeatureAPI.getInstance().shutdown();
    }


    @Given("a {} flagd provider")
    public void setupProvider(String providerType) {
        state.builder
                .deadline(500)
                .keepAlive(0)
                .retryGracePeriod(1);
        boolean wait = true;
        switch (providerType) {
            case "unavailable":
                this.state.providerType = ProviderType.SOCKET;
                state.builder.port(UNAVAILABLE_PORT);
                wait = false;
                break;
            case "socket":
                this.state.providerType = ProviderType.SOCKET;
                String socketPath = sharedTempDir.resolve("socket.sock").toAbsolutePath().toString();
                state.builder.socketPath(socketPath);
                state.builder.port(UNAVAILABLE_PORT);
                break;
            case "ssl":
                String path = "test-harness/ssl/custom-root-cert.crt";

                File file = new File(path);
                String absolutePath = file.getAbsolutePath();
                this.state.providerType = ProviderType.SSL;
                state
                        .builder
                        .port(getContainer().getPort(State.resolverType))
                        .tls(true)
                        .certPath(absolutePath);
                break;

            default:
                this.state.providerType = ProviderType.DEFAULT;
                state.builder.port(getContainer().getPort(State.resolverType));
                break;
        }
        FeatureProvider provider = new FlagdProvider(state.builder
                .resolverType(State.resolverType)
                .build());

        OpenFeatureAPI api = OpenFeatureAPI.getInstance();
        if (wait) {
            api.setProviderAndWait(providerType, provider);
        } else {
            api.setProvider(providerType, provider);
        }
        this.state.client = api.getClient(providerType);
    }

    @When("the connection is lost for {int}s")
    public void the_connection_is_lost_for(int seconds) throws InterruptedException {
        FlagdContainer container = getContainer();

/*        TimerTask task = new TimerTask() {
            public void run() {
                container.start();
                int port = container.getPort(State.resolverType);
            }
        };
        Timer timer = new Timer("Timer");*/

        LOG.info("stopping container for {}", state.providerType);
        container.stop();

        //timer.schedule(task, seconds * 1000L);
        Thread.sleep(seconds * 1000L);

        LOG.info("starting container for {}", state.providerType);
        container.start();
    }

    private FlagdContainer getContainer() {
        LOG.info("getting container for {}", state.providerType);
        System.out.println("getting container for " + state.providerType);
        return containers.getOrDefault(state.providerType, containers.get(ProviderType.DEFAULT));
    }
}
