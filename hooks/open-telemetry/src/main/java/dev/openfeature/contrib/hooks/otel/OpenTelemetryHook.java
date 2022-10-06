package dev.openfeature.contrib.hooks.otel;

import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.NoOpProvider;
import dev.openfeature.sdk.OpenFeatureAPI;

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
     * A test method...
     *
     * @return {boolean}
     */
    public static boolean test() {
        OpenFeatureAPI.getInstance().setProvider(new NoOpProvider());
        Client client = OpenFeatureAPI.getInstance().getClient();
        return client.getBooleanValue("test2", true);
    }

}
