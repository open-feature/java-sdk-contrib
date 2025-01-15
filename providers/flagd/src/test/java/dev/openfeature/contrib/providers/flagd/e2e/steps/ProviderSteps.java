package dev.openfeature.contrib.providers.flagd.e2e.steps;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.google.gson.JsonObject;
import dev.openfeature.contrib.providers.flagd.Config;
import dev.openfeature.contrib.providers.flagd.FlagdProvider;
import dev.openfeature.contrib.providers.flagd.e2e.FlagdContainer;
import dev.openfeature.contrib.providers.flagd.e2e.State;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.OpenFeatureAPI;
import eu.rekawek.toxiproxy.Proxy;
import eu.rekawek.toxiproxy.ToxiproxyClient;
import eu.rekawek.toxiproxy.model.ToxicDirection;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.parallel.Isolated;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.shaded.org.apache.commons.io.FileUtils;

@Isolated()
@Slf4j
public class ProviderSteps extends AbstractSteps {

    public static final int UNAVAILABLE_PORT = 9999;
    static Map<ProviderType, FlagdContainer> containers = new HashMap<>();
    public static Network network = Network.newNetwork();
    public static ToxiproxyContainer toxiproxy =
            new ToxiproxyContainer("ghcr.io/shopify/toxiproxy:2.5.0").withNetwork(network);
    public static ToxiproxyClient toxiproxyClient;

    static Path sharedTempDir;

    public ProviderSteps(State state) {
        super(state);
    }

    static String generateProxyName(Config.Resolver resolver, ProviderType providerType) {
        return providerType + "-" + resolver;
    }

    @BeforeAll
    public static void beforeAll() throws IOException {
        toxiproxy.start();
        toxiproxyClient = new ToxiproxyClient(toxiproxy.getHost(), toxiproxy.getControlPort());
        toxiproxyClient.createProxy(
                generateProxyName(Config.Resolver.RPC, ProviderType.DEFAULT), "0.0.0.0:8666", "default:8013");

        toxiproxyClient.createProxy(
                generateProxyName(Config.Resolver.IN_PROCESS, ProviderType.DEFAULT), "0.0.0.0:8667", "default:8015");
        toxiproxyClient.createProxy(
                generateProxyName(Config.Resolver.RPC, ProviderType.SSL), "0.0.0.0:8668", "ssl:8013");
        toxiproxyClient.createProxy(
                generateProxyName(Config.Resolver.IN_PROCESS, ProviderType.SSL), "0.0.0.0:8669", "ssl:8015");

        containers.put(
                ProviderType.DEFAULT, new FlagdContainer().withNetwork(network).withNetworkAliases("default"));
        containers.put(
                ProviderType.SSL, new FlagdContainer("ssl").withNetwork(network).withNetworkAliases("ssl"));
        sharedTempDir = Files.createDirectories(
                Paths.get("tmp/" + RandomStringUtils.randomAlphanumeric(8).toLowerCase() + "/"));
        containers.put(
                ProviderType.SOCKET,
                new FlagdContainer("socket")
                        .withFileSystemBind(sharedTempDir.toAbsolutePath().toString(), "/tmp", BindMode.READ_WRITE));
    }

    @AfterAll
    public static void afterAll() throws IOException {

        containers.forEach((name, container) -> container.stop());
        FileUtils.deleteDirectory(sharedTempDir.toFile());
        toxiproxyClient.reset();
        toxiproxy.stop();
    }

    @Before
    public void before() throws IOException {

        toxiproxyClient.getProxies().forEach(proxy -> {
            try {
                proxy.toxics().getAll().forEach(toxic -> {
                    try {
                        toxic.remove();
                    } catch (IOException e) {
                        log.debug("Failed to remove timout", e);
                    }
                });
            } catch (IOException e) {
                log.debug("Failed to remove timout", e);
            }
        });

        containers.values().stream()
                .filter(containers -> !containers.isRunning())
                .forEach(FlagdContainer::start);
    }

    @After
    public void tearDown() {
        OpenFeatureAPI.getInstance().shutdown();
    }

    public int getPort(Config.Resolver resolver, ProviderType providerType) {
        switch (resolver) {
            case RPC:
                switch (providerType) {
                    case DEFAULT:
                        return toxiproxy.getMappedPort(8666);
                    case SSL:
                        return toxiproxy.getMappedPort(8668);
                }
            case IN_PROCESS:
                switch (providerType) {
                    case DEFAULT:
                        return toxiproxy.getMappedPort(8667);
                    case SSL:
                        return toxiproxy.getMappedPort(8669);
                }
            default:
                throw new IllegalArgumentException("Unsupported resolver: " + resolver);
        }
    }

    @Given("a {} flagd provider")
    public void setupProvider(String providerType) throws IOException {
        state.builder.deadline(500).keepAlive(0).retryGracePeriod(3);
        boolean wait = true;
        switch (providerType) {
            case "unavailable":
                this.state.providerType = ProviderType.SOCKET;
                state.builder.port(UNAVAILABLE_PORT);
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
                        .port(getPort(State.resolverType, state.providerType))
                        .tls(true)
                        .certPath(absolutePath);
                break;
            case "offline":
                File flags = new File("test-harness/flags");
                ObjectMapper objectMapper = new ObjectMapper();
                Object merged = new Object();
                for (File listFile : Objects.requireNonNull(flags.listFiles())) {
                    ObjectReader updater = objectMapper.readerForUpdating(merged);
                    merged = updater.readValue(listFile, Object.class);
                }
                Path offlinePath = Files.createTempFile("flags", ".json");
                objectMapper.writeValue(offlinePath.toFile(), merged);

                state.builder
                        .port(UNAVAILABLE_PORT)
                        .offlineFlagSourcePath(offlinePath.toAbsolutePath().toString());
                break;

            default:
                this.state.providerType = ProviderType.DEFAULT;
                state.builder.port(getPort(State.resolverType, state.providerType));
                break;
        }
        FeatureProvider provider =
                new FlagdProvider(state.builder.resolverType(State.resolverType).build());

        OpenFeatureAPI api = OpenFeatureAPI.getInstance();
        if (wait) {
            api.setProviderAndWait(providerType, provider);
        } else {
            api.setProvider(providerType, provider);
        }
        this.state.client = api.getClient(providerType);
    }

    @When("the connection is lost for {int}s")
    public void the_connection_is_lost_for(int seconds) throws InterruptedException, IOException {
        log.info("Timeout and wait for {} seconds", seconds);
        String randomizer = RandomStringUtils.randomAlphanumeric(5);
        String timoutName = "restart" + randomizer;
        Proxy proxy = toxiproxyClient.getProxy(generateProxyName(State.resolverType, state.providerType));
        proxy.toxics().timeout(timoutName, ToxicDirection.UPSTREAM, seconds);

        TimerTask task = new TimerTask() {
            public void run() {
                try {
                    proxy.toxics().get(timoutName).remove();
                } catch (IOException e) {
                    log.debug("Failed to remove timout", e);
                }
            }
        };
        Timer restartTimer = new Timer("Timer" + randomizer);

        restartTimer.schedule(task, seconds * 1000L);
    }

    static FlagdContainer getContainer(ProviderType providerType) {
        log.info("getting container for {}", providerType);
        return containers.getOrDefault(providerType, containers.get(ProviderType.DEFAULT));
    }
}
