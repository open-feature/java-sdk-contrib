package dev.openfeature.contrib.samples.gcpparametermanager;

import dev.openfeature.contrib.providers.gcpparametermanager.GcpParameterManagerProvider;
import dev.openfeature.contrib.providers.gcpparametermanager.GcpParameterManagerProviderOptions;
import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.MutableContext;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.Value;
import java.time.Duration;

/**
 * Sample application demonstrating the GCP Parameter Manager OpenFeature provider.
 *
 * <p>This app evaluates five feature flags backed by GCP Parameter Manager parameters:
 * <ul>
 *   <li>{@code of-sample-dark-mode} (boolean) — whether the dark UI theme is enabled</li>
 *   <li>{@code of-sample-banner-text} (string) — hero banner copy shown to users</li>
 *   <li>{@code of-sample-max-cart-items} (integer) — maximum items allowed in the cart</li>
 *   <li>{@code of-sample-discount-rate} (double) — discount multiplier (0.0 – 1.0)</li>
 *   <li>{@code of-sample-checkout-config} (object/JSON) — structured checkout settings</li>
 * </ul>
 *
 * <p>Run {@code setup.sh} first to create these parameters in your GCP project, then:
 * <pre>
 *   export GCP_PROJECT_ID=my-gcp-project
 *   mvn exec:java
 * </pre>
 */
public class ParameterManagerSampleApp {

    private static final String PREFIX = "of-sample-";

    public static void main(String[] args) throws Exception {
        String projectId = resolveProjectId(args);

        System.out.println("=======================================================");
        System.out.println("  GCP Parameter Manager — OpenFeature Sample");
        System.out.println("=======================================================");
        System.out.println("Project  : " + projectId);
        System.out.println("Location : global");
        System.out.println("Prefix   : " + PREFIX);
        System.out.println();

        // Build provider options
        GcpParameterManagerProviderOptions options =
                GcpParameterManagerProviderOptions.builder()
                        .projectId(projectId)
                        .locationId("global") // use "us-central1" etc. for regional parameters
                        .parameterNamePrefix(PREFIX) // parameters named "of-sample-<flagKey>"
                        .cacheExpiry(Duration.ofSeconds(30))
                        .build();

        // Register the provider with OpenFeature
        GcpParameterManagerProvider provider = new GcpParameterManagerProvider(options);
        OpenFeatureAPI api = OpenFeatureAPI.getInstance();
        api.setProviderAndWait(provider);
        Client client = api.getClient();

        // Evaluation context (optional — demonstrates passing user context)
        MutableContext ctx = new MutableContext();
        ctx.add("userId", "user-42");

        // ── Boolean flag ────────────────────────────────────────────────────────────
        printHeader("Boolean Flag  »  dark-mode");
        boolean darkMode = client.getBooleanValue("dark-mode", false, ctx);
        System.out.println("Value   : " + darkMode);
        System.out.println("Effect  : " + (darkMode ? "Dark theme activated" : "Light theme active"));

        // ── String flag ─────────────────────────────────────────────────────────────
        printHeader("String Flag  »  banner-text");
        String bannerText = client.getStringValue("banner-text", "Welcome!", ctx);
        System.out.println("Value   : " + bannerText);

        // ── Integer flag ─────────────────────────────────────────────────────────────
        printHeader("Integer Flag  »  max-cart-items");
        int maxCartItems = client.getIntegerValue("max-cart-items", 10, ctx);
        System.out.println("Value   : " + maxCartItems);
        System.out.println("Effect  : Cart is capped at " + maxCartItems + " items");

        // ── Double flag ──────────────────────────────────────────────────────────────
        printHeader("Double Flag  »  discount-rate");
        double discountRate = client.getDoubleValue("discount-rate", 0.0, ctx);
        System.out.printf("Value   : %.2f%n", discountRate);
        System.out.printf("Effect  : %.0f%% discount applied to cart total%n", discountRate * 100);

        // ── Object flag (JSON) ───────────────────────────────────────────────────────
        printHeader("Object Flag  »  checkout-config");
        Value checkoutConfig = client.getObjectValue("checkout-config", new Value(), ctx);
        System.out.println("Value   : " + checkoutConfig);
        if (checkoutConfig.isStructure()) {
            Value paymentMethods = checkoutConfig.asStructure().getValue("paymentMethods");
            Value expressCheckout = checkoutConfig.asStructure().getValue("expressCheckout");
            System.out.println("Payment methods  : " + paymentMethods);
            System.out.println("Express checkout : " + expressCheckout);
        }

        System.out.println();
        System.out.println("=======================================================");
        System.out.println("  All flags evaluated successfully.");
        System.out.println("=======================================================");

        api.shutdown();
    }

    private static String resolveProjectId(String[] args) {
        // 1. CLI argument
        if (args.length > 0 && !args[0].isBlank()) {
            return args[0];
        }
        // 2. Environment variable
        String fromEnv = System.getenv("GCP_PROJECT_ID");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }
        // 3. System property (set via -DGCP_PROJECT_ID=... or exec plugin config)
        String fromProp = System.getProperty("GCP_PROJECT_ID");
        if (fromProp != null && !fromProp.isBlank()) {
            return fromProp;
        }
        System.err.println("ERROR: GCP_PROJECT_ID is not set.");
        System.err.println("Usage: export GCP_PROJECT_ID=my-project && mvn exec:java");
        System.err.println("  or:  mvn exec:java -DGCP_PROJECT_ID=my-project");
        System.exit(1);
        return null; // unreachable
    }

    private static void printHeader(String title) {
        System.out.println();
        System.out.println("── " + title + " " + "─".repeat(Math.max(0, 50 - title.length())));
    }
}
