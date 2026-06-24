package dev.openfeature.contrib.samples.gcp;

import dev.openfeature.contrib.providers.gcp.GcpParameterManagerProvider;
import dev.openfeature.contrib.providers.gcp.GcpProviderOptions;
import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.MutableContext;
import dev.openfeature.sdk.OpenFeatureAPI;
import java.time.Duration;

/**
 * Sample application demonstrating the GCP Parameter Manager OpenFeature provider.
 *
 * <p>This app evaluates the same five feature flags as the Secret Manager sample, but reads
 * values from GCP Parameter Manager parameters with names prefixed by {@code of-sample-}.
 */
public class ParameterManagerSampleApp extends AbstractGcpSampleApp {

    public static void main(String[] args) throws Exception {
        String projectId = resolveProjectId(args);

        System.out.println("=======================================================");
        System.out.println("  GCP Parameter Manager — OpenFeature Sample");
        System.out.println("=======================================================");
        System.out.println("Project : " + projectId);
        System.out.println("Prefix  : " + PREFIX);
        System.out.println();

        GcpProviderOptions options = GcpProviderOptions.builder()
            .projectId(projectId)
            .namePrefix(PREFIX)
            .cacheExpiry(Duration.ofSeconds(30))
            .build();

        GcpParameterManagerProvider provider = new GcpParameterManagerProvider(options);
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
