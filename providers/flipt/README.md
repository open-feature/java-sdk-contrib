# Official Flipt OpenFeature Provider for Java

[Flipt](https://www.flipt.io/) OpenFeature Provider can provide usage for Flipt via OpenFeature Java SDK.

## Installation

<!-- x-release-please-start-version -->

```xml

<dependency>
    <groupId>dev.openfeature.contrib.providers</groupId>
    <artifactId>flipt</artifactId>
    <version>0.0.1</version>
</dependency>
```

<!-- x-release-please-end-version -->

## Concepts
* Boolean evaluation gets feature boolean evaluation / enabled status.
* Non-boolean evaluation gets feature variant key.

## Usage
Flipt OpenFeature Provider is using Flipt Java SDK.

### Usage Example

```
FeatureProvider featureProvider = new FliptProvider(fliptProviderConfig);
OpenFeatureAPI.getInstance().setProviderAndWait(featureProvider);

MutableContext evaluationContext = new MutableContext();
evaluationContext.setTargetingKey(FLAG_NAME + "_targeting_key");
featureEnabled = fliptProvider.getBooleanEvaluation(FLAG_NAME, false, evaluationContext).getValue();
variant = fliptProvider.getStringEvaluation(VARIANT_FLAG_NAME, "", new ImmutableContext()).getValue());
```

See [FliptProviderTest.java](./src/test/java/dev/openfeature/contrib/providers/flipt/FliptProviderTest.java) for more information.

### Additional Usage Details

* Additional evaluation data can be received via flag metadata, such as:
  * *variant-attachment* - string

## Flipt Provider Tests Strategies

Unit test based on WireMock for API mocking.  
