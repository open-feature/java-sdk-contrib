# Unofficial Statsig OpenFeature Provider for Java

[Statsig](https://statsig.com/) OpenFeature Provider can provide usage for Statsig via OpenFeature Java SDK.

## Installation

<!-- x-release-please-start-version -->

```xml

<dependency>
    <groupId>dev.openfeature.contrib.providers</groupId>
    <artifactId>statsig</artifactId>
    <version>0.2.0</version>
</dependency>
```

<!-- x-release-please-end-version -->

## Concepts
* String/Integer/Double evaluations evaluation gets [Dynamic config](https://docs.statsig.com/server/javaSdk#reading-a-dynamic-config) or [Layer](https://docs.statsig.com/server/javaSdk#getting-an-layerexperiment) evaluation.
  As the key represents an inner attribute, feature config is required as a parameter with data needed for evaluation.
  For an example of dynamic config of product alias, need to differentiate between dynamic config or layer, and the dynamic config name.
* Boolean evaluation gets [gate](https://docs.statsig.com/server/javaSdk#checking-a-gate) status when feature config is not passed.
  When feature config exists, it evaluates to the config/layer attribute, similar to String/Integer/Float evaluations.
* Object evaluation gets a structure representing the dynamic config or layer.
* [Private Attributes](https://docs.statsig.com/server/javaSdk#private-attributes) are supported as 'privateAttributes' context key.

## Usage
Statsig OpenFeature Provider is based on [Statsig Java SDK documentation](https://docs.statsig.com/server/javaSdk).

### Usage Example

```java
StatsigOptions statsigOptions = new StatsigOptions();
StatsigProviderConfig statsigProviderConfig = StatsigProviderConfig.builder().sdkKey(sdkKey)
    .options(statsigOptions).build();
statsigProvider = new StatsigProvider(statsigProviderConfig);
OpenFeatureAPI.getInstance().setProviderAndWait(statsigProvider);

MutableContext evaluationContext = new MutableContext();
evaluationContext.setTargetingKey(TARGETING_KEY);
boolean featureEnabled = client.getBooleanValue(FLAG_NAME, false);

MutableContext featureConfig = new MutableContext();
featureConfig.add("type", "CONFIG");
featureConfig.add("name", "product");
evaluationContext.add("feature_config", featureConfig);
String value = statsigProvider.getStringEvaluation("alias", "fallback", evaluationContext).getValue());

MutableContext evaluationContext = new MutableContext();
evaluationContext.setTargetingKey("test-id");
evaluationContext.add("Email", "a@b.com");
MutableContext privateAttributes = new MutableContext();
privateAttributes.add("locale", locale);
evaluationContext.add("privateAttributes", privateAttributes);
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


