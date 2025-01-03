package dev.openfeature.contrib.providers.flagd.e2e.steps;

import dev.openfeature.contrib.providers.flagd.FlagdProvider;
import dev.openfeature.contrib.providers.flagd.e2e.FlagdContainer;
import dev.openfeature.contrib.providers.flagd.e2e.State;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.OpenFeatureAPI;
import io.cucumber.java.AfterAll;
import io.cucumber.java.BeforeAll;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import org.junit.jupiter.api.parallel.Isolated;

@Isolated()
public class ProviderSteps extends AbstractSteps {

    static Map<ProviderType, FlagdContainer> containers = new HashMap<>();
    static String tmpdir;

    public ProviderSteps(State state) {
        super(state);
    }

    @BeforeAll
    public static void beforeAll() throws IOException {
        containers.put(ProviderType.DEFAULT, new FlagdContainer());
        containers.put(ProviderType.SSL, new FlagdContainer("ssl"));
        tmpdir = Files.createTempDirectory("flagd").toFile().getAbsolutePath();
        // containers.put(ProviderType.SOCKET, new FlagdContainer("socket").withFileSystemBind(tmpdir, "/tmp",
        // BindMode.READ_WRITE));

        containers.forEach((name, container) -> container.start());
    }

    @AfterAll
    public static void afterAll() {
        containers.forEach((name, container) -> container.stop());
    }

    @Given("a {} flagd provider")
    public void setupProvider(String providerType) {
        state.builder.deadline(500).keepAlive(0).maxEventStreamRetries(2);
        boolean wait = true;
        switch (providerType) {
            case "unavailable":
                this.state.providerType = ProviderType.SOCKET;
                state.builder.port(9999);
                wait = false;
                break;
            case "socket":
                this.state.providerType = ProviderType.SOCKET;
                state.builder.socketPath(tmpdir);
                break;
            case "ssl":
                String path = "test-harness/ssl/custom-root-cert.crt";

                File file = new File(path);
                String absolutePath = file.getAbsolutePath();
                this.state.providerType = ProviderType.SSL;
                state.builder
                        .port(getContainer().getPort(State.resolverType))
                        .tls(true)
                        .certPath(absolutePath);
                break;

            default:
                this.state.providerType = ProviderType.DEFAULT;
                state.builder.port(getContainer().getPort(State.resolverType));
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
    public void the_connection_is_lost_for(int seconds) {
        FlagdContainer container = getContainer();

        TimerTask task = new TimerTask() {
            public void run() {
                container.start();
            }
        };
        Timer timer = new Timer("Timer");

        timer.schedule(task, seconds * 1000L);
    }

    private FlagdContainer getContainer() {
        return containers.getOrDefault(state.providerType, containers.get(ProviderType.DEFAULT));
    }
}
