# Unofficial Unleash OpenFeature Provider for Java

Unleash OpenFeature Provider can provide usage for Unleash via OpenFeature Java SDK.

## Installation

<!-- x-release-please-start-version -->

```xml

<dependency>
    <groupId>dev.openfeature.contrib.providers</groupId>
    <artifactId>unleash</artifactId>
    <version>0.0.1</version>
</dependency>
```

<!-- x-release-please-end-version -->

## Concepts
* Boolean evaluation gets feature enabled status.
* String evaluation gets feature variant value.

## Usage
Unleash OpenFeature Provider is using Unleash Java SDK.

### Usage Example

```
FeatureProvider featureProvider = new UnleashProvider(unleashOptions);
OpenFeatureAPI.getInstance().setProviderAndWait(unleashProvider);
boolean featureEnabled = client.getBooleanValue(FLAG_NAME, false);

UnleashContext unleashContext = UnleashContext.builder().userId("1").build();
EvaluationContext evaluationContext = UnleashProvider.transform(unleashContext);
featureEnabled = client.getBooleanValue(FLAG_NAME, false, evaluationContext);

String variantValue = client.getStringValue(FLAG_NAME, "");
```

See [UnleashProviderTest.java](./src/test/java/dev/openfeature/contrib/providers/unleash/UnleashProviderTest.java) for more information.

## Caveats / Limitations

* Unleash OpenFeature Provider only supports boolean and string evaluation.
* Unleash OpenFeature Provider only supports string variant type.

## Unleash Provider Tests Strategies

Unit test based on Unleash instance with Unleash features schema file, with WireMock for API mocking.  
See [UnleashProviderTest.java](./src/test/java/dev/openfeature/contrib/providers/unleash/UnleashProviderTest.java) for more information.

## References
* [Unleash](https://getunleash.io)
