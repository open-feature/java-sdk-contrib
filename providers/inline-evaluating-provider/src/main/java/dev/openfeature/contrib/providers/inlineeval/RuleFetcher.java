package dev.openfeature.contrib.providers.inlineeval;

import dev.openfeature.sdk.EvaluationContext;

public interface RuleFetcher {

    /**
     * Called to set up the client initially. This is used to pre-fetch initial data as well as setup mechanisms to stay up
     * to date.
     * @param initialContext application context known thus far
     */
    void initialize(EvaluationContext initialContext);

    /**
     * Given a key name, return the JSONLogic rules for it
     * @param key The key to fetch logic for
     * @return json logic rules or null
     */
    String getRuleForKey(String key);
}
