# Unofficial ConfigCat OpenFeature Provider for Java

[ConfigCat](https://configcat.com/) OpenFeature Provider can provide usage for ConfigCat via OpenFeature Java SDK.

## Installation

<!-- x-release-please-start-version -->

```xml

<dependency>
    <groupId>dev.openfeature.contrib.providers</groupId>
    <artifactId>configcat</artifactId>
    <version>0.0.3</version>
</dependency>
```

<!-- x-release-please-end-version -->

## Usage
ConfigCat OpenFeature Provider is using ConfigCat Java SDK.

### Usage Example

```
ConfigCatProviderConfig configCatProviderConfig = ConfigCatProviderConfig.builder().sdkKey(sdkKey).build();
configCatProvider = new ConfigCatProvider(configCatProviderConfig);
OpenFeatureAPI.getInstance().setProviderAndWait(configCatProvider);
boolean featureEnabled = client.getBooleanValue(FLAG_NAME, false);

MutableContext evaluationContext = new MutableContext();
evaluationContext.setTargetingKey("csp@matching.com");
evaluationContext.add("Email", "a@b.com");
evaluationContext.add("Country", "someCountry");
featureEnabled = client.getBooleanValue(USERS_FLAG_NAME, false, evaluationContext);
```

See [ConfigCatProviderTest.java](./src/test/java/dev/openfeature/contrib/providers/configcat/ConfigCatProviderTest.java)
for more information.

## Notes
Some ConfigCat custom operations are supported from the provider client via:

```java
configCatProvider.getConfigCatClient()...
```

## ConfigCat Provider Tests Strategies

Unit test based on ConfigCat local features file.  
See [ConfigCatProviderTest.java](./src/test/java/dev/openfeature/contrib/providers/configcat/ConfigCatProviderTest.java)
for more information.
