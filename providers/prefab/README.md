# Unofficial Prefab OpenFeature Provider for Java

[Prefab](https://www.prefab.cloud/) OpenFeature Provider can provide usage for Prefab via OpenFeature Java SDK.

## Installation

<!-- x-release-please-start-version -->

```xml

<dependency>
    <groupId>dev.openfeature.contrib.providers</groupId>
    <artifactId>prefab</artifactId>
    <version>0.0.1</version>
</dependency>
```

<!-- x-release-please-end-version -->

## Usage
Prefab OpenFeature Provider is using Prefab Java SDK.

### Usage Example

```
PrefabProviderConfig prefabProviderConfig = PrefabProviderConfig.builder().sdkKey(sdkKey).build();
prefabProvider = new PrefabProvider(prefabProviderConfig);
OpenFeatureAPI.getInstance().setProviderAndWait(prefabProvider);


Options options = new Options().setApikey(sdkKey);
PrefabProviderConfig prefabProviderConfig = PrefabProviderConfig.builder()
    .options(options).build();
PrefabProvider prefabProvider = new PrefabProvider(prefabProviderConfig);
OpenFeatureAPI.getInstance().setProviderAndWait(prefabProvider);

boolean featureEnabled = client.getBooleanValue(FLAG_NAME, false);

MutableContext evaluationContext = new MutableContext();
evaluationContext.add("user.key", "key1");
evaluationContext.add("team.domain", "prefab.cloud");
featureEnabled = client.getBooleanValue(USERS_FLAG_NAME, false, evaluationContext);
```

See [PrefabProviderTest](./src/test/java/dev/openfeature/contrib/providers/prefab/PrefabProviderTest.java)
for more information.

## Notes
Some Prefab custom operations are supported from the provider client via:

```java
prefabProvider.getPrefabCloudClient()...
```

## Prefab Provider Tests Strategies

Unit test based on Prefab local features file.  
See [PrefabProviderTest](./src/test/java/dev/openfeature/contrib/providers/prefab/PrefabProviderTest.java)
for more information.
