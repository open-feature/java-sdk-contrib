package dev.openfeature.contrib.hooks.otel;

import dev.openfeature.javasdk.Client;
import dev.openfeature.javasdk.NoOpProvider;
import dev.openfeature.javasdk.OpenFeatureAPI;

/** 
 * A placeholder.
 */
public class OpenTelemetryHook {
    
    /** 
     * Create a new OpenTelemetryHook instance.
     */
    public OpenTelemetryHook() {
    }

    /** 
     * A test method....
     * @return {boolean}
     */
    public static boolean test() {
        OpenFeatureAPI.getInstance().setProvider(new NoOpProvider());
        Client client = OpenFeatureAPI.getInstance().getClient();
        return client.getBooleanValue("test2", true);
    }

}
