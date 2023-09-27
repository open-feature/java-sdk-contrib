# Unofficial Togglz OpenFeature Provider for Java

Togglz OpenFeature Provider can provide usage for Togglz via OpenFeature Java SDK.

## Installation

<!-- x-release-please-start-version -->

```xml

<dependency>
    <groupId>dev.openfeature.contrib.providers</groupId>
    <artifactId>togglz</artifactId>
    <version>0.0.1</version>
</dependency>
```

<!-- x-release-please-end-version -->

## Usage
Togglz OpenFeature Provider is using Togglz Java SDK.

### Usage Example

```
TogglzOptions togglzOptions = TogglzOptions.builder().features(features).build();
FeatureProvider featureProvider = new TogglzProvider(togglzOptions);
api.setProviderAndWait(featureProvider);
client = api.getClient();
boolean featureEnabled = client.getBooleanValue(TestFeatures.FEATURE_ONE.name(), false);

```

See [TogglzProviderTest.java](./src/test/java/dev/openfeature/contrib/providers/togglz/TogglzProviderTest.java) for more information.

## Caveats / Limitations

* Togglz OpenFeature Provider only supports boolean feature flags.
* Evaluation does not treat evaluation context, but relies on Togglz functionalities.

## References
* [Togglz](https://www.togglz.org)
