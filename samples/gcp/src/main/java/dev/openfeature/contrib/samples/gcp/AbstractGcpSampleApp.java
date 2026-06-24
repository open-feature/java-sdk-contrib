package dev.openfeature.contrib.samples.gcp;

import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.MutableContext;
import dev.openfeature.sdk.Value;

abstract class AbstractGcpSampleApp {

    static final String PREFIX = "of-sample-";

    static String resolveProjectId(String[] args) {
        if (args.length > 0 && !args[0].isBlank()) {
            return args[0];
        }

        String fromEnv = System.getenv("GCP_PROJECT_ID");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }

        String fromProp = System.getProperty("GCP_PROJECT_ID");
        if (fromProp != null && !fromProp.isBlank()) {
            return fromProp;
        }

        System.err.println("ERROR: GCP_PROJECT_ID is not set.");
        System.err.println("Usage: export GCP_PROJECT_ID=my-project && mvn exec:java");
        System.err.println("  or:  mvn exec:java -DGCP_PROJECT_ID=my-project");
        System.exit(1);
        return null;
    }

    static void printHeader(String title) {
        System.out.println();
        System.out.println("── " + title + " " + "─".repeat(Math.max(0, 50 - title.length())));
    }

    static void evaluateCommonFlags(Client client, MutableContext ctx) {
        printHeader("Boolean Flag  »  dark-mode");
        boolean darkMode = client.getBooleanValue("dark-mode", false, ctx);
        System.out.println("Value   : " + darkMode);
        System.out.println("Effect  : " + (darkMode ? "Dark theme activated" : "Light theme active"));

        printHeader("String Flag  »  banner-text");
        String bannerText = client.getStringValue("banner-text", "Welcome!", ctx);
        System.out.println("Value   : " + bannerText);

        printHeader("Integer Flag  »  max-cart-items");
        int maxCartItems = client.getIntegerValue("max-cart-items", 10, ctx);
        System.out.println("Value   : " + maxCartItems);
        System.out.println("Effect  : Cart is capped at " + maxCartItems + " items");

        printHeader("Double Flag  »  discount-rate");
        double discountRate = client.getDoubleValue("discount-rate", 0.0, ctx);
        System.out.printf("Value   : %.2f%n", discountRate);
        System.out.printf("Effect  : %.0f%% discount applied to cart total%n", discountRate * 100);

        printHeader("Object Flag  »  checkout-config");
        Value checkoutConfig = client.getObjectValue("checkout-config", new Value(), ctx);
        System.out.println("Value   : " + checkoutConfig);
        if (checkoutConfig.isStructure()) {
            Value paymentMethods = checkoutConfig.asStructure().getValue("paymentMethods");
            Value expressCheckout = checkoutConfig.asStructure().getValue("expressCheckout");
            System.out.println("Payment methods  : " + paymentMethods);
            System.out.println("Express checkout : " + expressCheckout);
        }
    }
}
