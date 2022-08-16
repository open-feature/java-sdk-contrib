package dev.openfeature.contrib.providers.flagd;

import dev.openfeature.javasdk.Client;
import dev.openfeature.javasdk.NoOpProvider;
import dev.openfeature.javasdk.OpenFeatureAPI;

/** 
 * A placeholder.
 */
public class FlagdProvider {

    /** 
     * Create a new FlagdProvider instance.
     */
    public FlagdProvider() {
    }

    /** 
     * A test method.,,
     * 
     * @return {boolean}
     */
    public static boolean test() {
        OpenFeatureAPI.getInstance().setProvider(new NoOpProvider());
        Client client = OpenFeatureAPI.getInstance().getClient();
        return client.getBooleanValue("test", true);
    }

}
