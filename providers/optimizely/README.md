# Unofficial Optimizely OpenFeature Provider for Java

[optimizely](https://www.optimizely.com/optimization-glossary/feature-flags/) OpenFeature Provider can provide usage for optimizely via OpenFeature Java SDK.

## Installation

<!-- x-release-please-start-version -->

```xml

<dependency>
    <groupId>dev.openfeature.contrib.providers</groupId>
    <artifactId>optimizely</artifactId>
    <version>0.0.1</version>
</dependency>
```

<!-- x-release-please-end-version -->

## Concepts

* Boolean evaluation gets feature  [enabled](https://docs.developers.optimizely.com/feature-experimentation/docs/create-feature-flags) value.
* Object evaluation gets a structure representing the evaluated variant variables.
* String/Integer/Double evaluations evaluation are not directly supported by Optimizely provider, use getObjectEvaluation instead.

## Usage
Optimizely OpenFeature Provider is based on [Optimizely Java SDK documentation](https://docs.developers.optimizely.com/feature-experimentation/docs/java-sdk).

### Usage Example

```java
OptimizelyProviderConfig config = OptimizelyProviderConfig.builder()
    .build();

provider = new OptimizelyProvider(config);
provider.initialize(new MutableContext("test-targeting-key"));

ProviderEvaluation<Boolean> evaluation = provider.getBooleanEvaluation("string-feature", false, ctx);
System.out.println("Feature enabled: " + evaluation.getValue());

ProviderEvaluation<Value> result = provider.getObjectEvaluation("string-feature", new Value(), ctx);
System.out.println("Feature variable: " + result.getValue().asStructure().getValue("string_variable_1").asString());
```

See [OptimizelyProviderTest](./src/test/java/dev/openfeature/contrib/providers/optimizely/OptimizelyProviderTest.java)
for more information.

## Notes
Some Optimizely custom operations are supported from the optimizely client via:

```java
provider.getOptimizely()...
```

## Optimizely Provider Tests Strategies

Unit test based on optimizely [Local Data File](https://docs.developers.optimizely.com/feature-experimentation/docs/initialize-sdk-java).
See [OptimizelyProviderTest](./src/test/java/dev/openfeature/contrib/providers/optimizely/OptimizelyProviderTest.java)
for more information.
