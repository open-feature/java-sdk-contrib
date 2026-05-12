package dev.openfeature.contrib.providers.gcp;

import static org.assertj.core.api.Assertions.assertThat;

import dev.openfeature.sdk.ImmutableContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

/**
 * Integration tests for {@link GcpSecretManagerProvider}.
 *
 * <p>These tests require real GCP credentials and a pre-configured project with test secrets.
 * They are excluded from the default test run via {@code @Tag("integration")}.
 *
 * <p>Pre-requisites:
 * <ol>
 *   <li>Set the {@code GCP_PROJECT_ID} environment variable to your GCP project ID.</li>
 *   <li>Ensure Application Default Credentials are configured
 *       ({@code gcloud auth application-default login}).</li>
 *   <li>Create the following secrets in GCP Secret Manager under the project (each with a
 *       {@code "latest"} version):
 *     <ul>
 *       <li>{@code it-bool-flag} with value {@code "true"}</li>
 *       <li>{@code it-string-flag} with value {@code "hello"}</li>
 *       <li>{@code it-int-flag} with value {@code "99"}</li>
 *       <li>{@code it-double-flag} with value {@code "2.71"}</li>
 *       <li>{@code it-object-flag} with value {@code {"key":"val"}}</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <p>To run these tests:
 * <pre>{@code
 * GCP_PROJECT_ID=my-project mvn verify -pl providers/gcp -Dgroups=integration
 * }</pre>
 */
@Tag("integration")
@DisabledIfEnvironmentVariable(named = "GCP_PROJECT_ID", matches = "")
@DisplayName("GcpSecretManagerProvider integration tests")
class GcpSecretManagerProviderIntegrationTest {

    private GcpSecretManagerProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        String projectId = System.getenv("GCP_PROJECT_ID");
        GcpSecretManagerProviderOptions opts =
                GcpSecretManagerProviderOptions.builder().projectId(projectId).build();
        provider = new GcpSecretManagerProvider(opts);
        provider.initialize(new ImmutableContext());
    }

    @AfterEach
    void tearDown() {
        provider.shutdown();
    }

    @Test
    @DisplayName("evaluates boolean secret")
    void booleanFlag() {
        assertThat(provider.getBooleanEvaluation("it-bool-flag", false, new ImmutableContext())
                        .getValue())
                .isTrue();
    }

    @Test
    @DisplayName("evaluates string secret")
    void stringFlag() {
        assertThat(provider.getStringEvaluation("it-string-flag", "", new ImmutableContext())
                        .getValue())
                .isEqualTo("hello");
    }

    @Test
    @DisplayName("evaluates integer secret")
    void integerFlag() {
        assertThat(provider.getIntegerEvaluation("it-int-flag", 0, new ImmutableContext())
                        .getValue())
                .isEqualTo(99);
    }

    @Test
    @DisplayName("evaluates double secret")
    void doubleFlag() {
        assertThat(provider.getDoubleEvaluation("it-double-flag", 0.0, new ImmutableContext())
                        .getValue())
                .isEqualTo(2.71);
    }

    @Test
    @DisplayName("evaluates object secret as Value/Structure")
    void objectFlag() {
        assertThat(provider.getObjectEvaluation("it-object-flag", null, new ImmutableContext())
                        .getValue()
                        .asStructure()
                        .getValue("key")
                        .asString())
                .isEqualTo("val");
    }
}
