# JSONLogic Evaluation Provider

This provider does inline evaluation (e.g. no hot-path remote calls) based on [JSONLogic](https://jsonlogic.com/). This should allow you to 
achieve low latency flag evaluation.

## Installation

<!-- x-release-please-start-version -->
```xml

<dependency>
    <groupId>dev.openfeature.contrib.providers</groupId>
    <artifactId>jsonlogic-eval-provider</artifactId>
    <version>1.2.0</version>
</dependency>
```
<!-- x-release-please-end-version -->

## Usage

You will need to create a custom class which implements the `RuleFetcher` interface. This code should cache your 
rules locally. During the `initialization` method, it should also set up a mechanism to stay up to date with remote 
flag changes. You can see `FileBasedFetcher` as a simplified example.

```java
JsonlogicProvider jlp = new JsonlogicProvider(new RuleFetcher() {
    @Override
    public void initialize(EvaluationContext initialContext) {
        // setup initial fetch & stay-up-to-date logic
    }

    @Nullable
    @Override
    public String getRuleForKey(String key) {
        // return the jsonlogic rule in string format for a given flag key
        return null;
    }
})

OpenFeature.setProvider(jlp);
```