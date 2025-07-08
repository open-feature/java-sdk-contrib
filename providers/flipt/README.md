# Official Flipt OpenFeature Provider for Java

[Flipt](https://www.flipt.io/) OpenFeature Provider can provide usage for Flipt via OpenFeature Java SDK.

## Installation

<!-- x-release-please-start-version -->

```xml
<dependency>
    <groupId>dev.openfeature.contrib.providers</groupId>
    <artifactId>flipt</artifactId>
    <version>0.1.2</version>
</dependency>
```

<!-- x-release-please-end-version -->

## Concepts

* Boolean evaluation gets feature boolean evaluation / enabled status.
* Object evaluation gets variant attachment.
* Other evaluations gets feature variant key.

## Usage

Flipt OpenFeature Provider uses Flipt's [Server Java SDK](https://github.com/flipt-io/flipt-server-sdks/tree/main/flipt-java).

### Usage Example

```java
// create a Flipt client and provider
FliptClientBuilder fliptClientBuilder = FliptClient.builder().url(apiUrl);
FliptProviderConfig fliptProviderConfig = FliptProviderConfig.builder()
        .fliptClientBuilder(fliptClientBuilder)
        .build();

// create OpenFeature provider
FeatureProvider fliptProvider = new FliptProvider(fliptProviderConfig);
OpenFeatureAPI.getInstance().setProviderAndWait("sync", fliptProvider);
client = OpenFeatureAPI.getInstance().getClient("sync");

MutableContext evaluationContext = new MutableContext();
evaluationContext.setTargetingKey(FLAG_NAME + "_targeting_key");
featureEnabled = client.getBooleanValue(FLAG_NAME, false, evaluationContext);
variant = client.getStringValue(VARIANT_FLAG_NAME, "", evaluationContext);
```

See [FliptProviderTest.java](./src/test/java/dev/openfeature/contrib/providers/flipt/FliptProviderTest.java) for more information.

### Additional Usage Details

* Additional evaluation data can be received via flag metadata, such as:
  * *variant-attachment* - string

## Flipt Provider Tests Strategies

Unit test based on WireMock for API mocking.  
