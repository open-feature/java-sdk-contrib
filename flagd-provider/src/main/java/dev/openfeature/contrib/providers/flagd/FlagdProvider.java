package dev.openfeature.contrib.providers.flagd;

import dev.openfeature.javasdk.Client;
import dev.openfeature.javasdk.NoOpProvider;
import dev.openfeature.javasdk.OpenFeatureAPI;

class FlagdProvider {

    public static boolean test() {
        OpenFeatureAPI.getInstance().setProvider(new NoOpProvider());
        Client client = OpenFeatureAPI.getInstance().getClient();
        return client.getBooleanValue("test", true);
    }

}
