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

### Evaluation Context

The `targetingKey` is required and maps to the Optimizely user ID. Additional attributes are passed to Optimizely for audience targeting.

### Variable Key Selection

Optimizely flags can have multiple variables. By default, the provider looks for a variable named `"value"`. Specify a different variable using the `variableKey` attribute:

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

## Release Notes

### 0.1.0

Concepts updated, evaluation acts accordingly.
