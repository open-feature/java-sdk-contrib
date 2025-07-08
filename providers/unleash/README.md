# Unofficial Unleash OpenFeature Provider for Java

[Unleash](https://getunleash.io) OpenFeature Provider can provide usage for Unleash via OpenFeature Java SDK.

## Installation

<!-- x-release-please-start-version -->

```xml

<dependency>
    <groupId>dev.openfeature.contrib.providers</groupId>
    <artifactId>unleash</artifactId>
    <version>0.1.1-alpha</version>
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
FeatureProvider unleashProvider = new UnleashProvider(unleashProviderConfig);
OpenFeatureAPI.getInstance().setProviderAndWait(unleashProvider);
boolean featureEnabled = client.getBooleanValue(FLAG_NAME, false);

// Context parameters are optional, not mandatory to fill all parameters
MutableContext evaluationContext = new MutableContext();
evaluationContext.add("userId", userIdValue);
evaluationContext.add("currentTime", String.valueOf(currentTimeValue));
evaluationContext.add("sessionId", sessionIdValue);
evaluationContext.add("remoteAddress", remoteAddressValue);
evaluationContext.add("environment", environmentValue);
evaluationContext.add("appName", appNameValue);
evaluationContext.add(customPropertyKey, customPropertyValue);
featureEnabled = client.getBooleanValue(FLAG_NAME, false, evaluationContext);

String variantValue = client.getStringValue(FLAG_NAME, "");
```

See [UnleashProviderTest.java](./src/test/java/dev/openfeature/contrib/providers/unleash/UnleashProviderTest.java) for more information.

### Additional Usage Details

* When default value is used and returned, default variant is not used and variant name is not set.
* json/csv payloads are evaluated via object evaluation as what returned from Unleash - string, wrapped with Value.
* Additional evaluation data can be received via flag metadata, such as:
  * *enabled* - boolean
  * *variant-stickiness* - string
  * *payload-type* - string, optional

## Unleash Provider Tests Strategies

Unit test based on Unleash instance with Unleash features schema file, with WireMock for API mocking.  
See [UnleashProviderTest.java](./src/test/java/dev/openfeature/contrib/providers/unleash/UnleashProviderTest.java) for more information.
