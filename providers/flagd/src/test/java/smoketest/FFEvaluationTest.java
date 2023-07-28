package smoketest;

import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.FlagdProvider;
import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.OpenFeatureAPI;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class FFEvaluationTest {

    private final String ffKey = "myFlag";

    private final String flagsPath = getClass().getClassLoader().getResource("flags.json").getPath();
    @Container
    private final GenericContainer<?> flagd = new GenericContainer<>("ghcr.io/open-feature/flagd:v0.6.1")
            .withExposedPorts(8013)
            .withFileSystemBind(flagsPath, "/etc/flags.json", BindMode.READ_ONLY)
            .withCommand("start -f file:/etc/flags.json");

    static OpenFeatureAPI api;
    private FlagdProvider provider;

    @BeforeAll
    public static void init() {
        api = OpenFeatureAPI.getInstance();
    }

    @BeforeEach
    void setUp() {
        flagd.start();
        flagd.waitingFor(Wait.forLogMessage("watching filepath", 1));
        provider = new FlagdProvider(FlagdOptions.builder().port(flagd.getMappedPort(8013)).build());
        api.setProvider(provider);
    }

    @Test
    void FFEvaluationOverrideDefaultValue() {
        Client client = api.getClient();
        boolean val = client.getBooleanValue(ffKey, false);
        assertTrue(val);
    }
}
