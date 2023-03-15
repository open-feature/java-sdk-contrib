package dev.openfeature.contrib.providers.inlineeval;

import dev.openfeature.sdk.EvaluationContext;

public class ExampleFetcher implements RuleFetcher {

    @Override
    public String getRuleForKey(String key) {
        // In a real version of this, these rules should be cached.
        return """
                {"if": [
                  {"or": [
                    {"in": [{"var": "userId"}, [1,2,3,4]]},
                    {"in": [{"var": "category"}, ["pies", "cakes"]]},
                  ]},
                  true,
                  false
                ]}
                """;
    }

    @Override public void initialize(EvaluationContext initialContext) {}
}
