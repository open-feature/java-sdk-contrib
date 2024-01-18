# Unofficial Statsig OpenFeature Provider for Java

[Statsig](https://statsig.com/) OpenFeature Provider can provide usage for Statsig via OpenFeature Java SDK.

## Installation

<!-- x-release-please-start-version -->

```xml

<dependency>
    <groupId>dev.openfeature.contrib.providers</groupId>
    <artifactId>statsig</artifactId>
    <version>0.0.1</version>
</dependency>
```

<!-- x-release-please-end-version -->

## Concepts
* Boolean evaluation gets [gate](https://docs.statsig.com/server/javaSdk#checking-a-gate) status.
* Non-boolean evaluation gets [Dynamic config](https://docs.statsig.com/server/javaSdk#reading-a-dynamic-config) and [Layer](https://docs.statsig.com/server/javaSdk#getting-an-layerexperiment) evaluation.
  Feature key structure is $TYPE[CONFIG/LAYER].$NAME.$KEY, for example _config.product.revision_ will evaluate dynamic config named "product" with "revision" key in it.
* [Private Attributes](https://docs.statsig.com/server/javaSdk#private-attributes) are supported as 'privateAttributes' context key.

## Usage
Statsig OpenFeature Provider is based on [Statsig Java SDK documentation](https://docs.statsig.com/server/javaSdk).

### Usage Example

```
StatsigOptions statsigOptions = new StatsigOptions();
StatsigProviderConfig statsigProviderConfig = StatsigProviderConfig.builder().sdkKey(sdkKey)
    .options(statsigOptions).build();
statsigProvider = new StatsigProvider(statsigProviderConfig);
OpenFeatureAPI.getInstance().setProviderAndWait(statsigProvider);

boolean featureEnabled = client.getBooleanValue(FLAG_NAME, false);
String featureValue = statsigProvider.getStringEvaluation("config.product.name", "",
    new ImmutableContext()).getValue());

MutableContext evaluationContext = new MutableContext();
evaluationContext.setTargetingKey("test-id");
evaluationContext.add("Email", "a@b.com");
MutableContext privateAttributes = new MutableContext();
privateAttributes.add(CONTEXT_LOCALE, locale);
evaluationContext.add(CONTEXT_PRIVATE_ATTRIBUTES, privateAttributes);
featureEnabled = client.getBooleanValue(USERS_FLAG_NAME, false, evaluationContext);
```

See [StatsigProviderTest](./src/test/java/dev/openfeature/contrib/providers/statsig/StatsigProviderTest.java)
for more information.

## Notes
Some Statsig custom operations are supported from the Statsig client via:

```java
Statsig...
```

## Statsig Provider Tests Strategies

Unit test based on Statsig [Local Overrides](https://docs.statsig.com/server/javaSdk#local-overrides) and mocking. 
As it is limited, evaluation context based tests are limited.
See [statsigProviderTest](./src/test/java/dev/openfeature/contrib/providers/statsig/StatsigProviderTest.java)
for more information.
