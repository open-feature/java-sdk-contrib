package dev.openfeature.contrib.samples.gcp;

import dev.openfeature.contrib.providers.gcp.GcpSecretManagerProvider;
import dev.openfeature.contrib.providers.gcp.GcpProviderOptions;
import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.MutableContext;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.Value;
import java.time.Duration;

/**
 * Sample application demonstrating the GCP Secret Manager OpenFeature provider.
 *
 * <p>This app evaluates five feature flags backed by GCP Secret Manager secrets:
 * <ul>
 *   <li>{@code of-sample-dark-mode} (boolean) — whether the dark UI theme is enabled</li>
 *   <li>{@code of-sample-banner-text} (string) — hero banner copy shown to users</li>
 *   <li>{@code of-sample-max-cart-items} (integer) — maximum items allowed in the cart</li>
 *   <li>{@code of-sample-discount-rate} (double) — discount multiplier (0.0 – 1.0)</li>
 *   <li>{@code of-sample-checkout-config} (object/JSON) — structured checkout settings</li>
 * </ul>
 *
 * <p>Run {@code setup.sh} first to create these secrets in your GCP project, then:
 * <pre>
 *   export GCP_PROJECT_ID=my-gcp-project
 *   mvn exec:java
 * </pre>
 */
public class SecretManagerSampleApp extends AbstractGcpSampleApp {

    public static void main(String[] args) throws Exception {
        String projectId = resolveProjectId(args);

        System.out.println("=======================================================");
        System.out.println("  GCP Secret Manager — OpenFeature Sample");
        System.out.println("=======================================================");
        System.out.println("Project : " + projectId);
        System.out.println("Prefix  : " + PREFIX);
        System.out.println();

        GcpProviderOptions options = GcpProviderOptions.builder()
            .projectId(projectId)
            .namePrefix(PREFIX) // secrets are named "of-sample-<flagKey>"
            .version("latest")
            .cacheExpiry(Duration.ofSeconds(30))
            .build();

        GcpSecretManagerProvider provider = new GcpSecretManagerProvider(options);
        OpenFeatureAPI api = OpenFeatureAPI.getInstance();
        api.setProviderAndWait(provider);
        Client client = api.getClient();

        MutableContext ctx = new MutableContext();
        ctx.add("userId", "user-42");

        evaluateCommonFlags(client, ctx);

        System.out.println();
        System.out.println("=======================================================");
        System.out.println("  All flags evaluated successfully.");
        System.out.println("=======================================================");

        api.shutdown();
    }
}
