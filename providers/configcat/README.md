# ConfigCat OpenFeature Provider for Java

[ConfigCat](https://configcat.com/) OpenFeature Provider can provide usage for ConfigCat via the OpenFeature Java SDK.

## Installation

<!-- x-release-please-start-version -->

```xml
<dependency>
    <groupId>dev.openfeature.contrib.providers</groupId>
    <artifactId>configcat</artifactId>
    <version>0.2.0</version>
</dependency>
```

<!-- x-release-please-end-version -->

## Usage
The ConfigCat OpenFeature Provider uses a ConfigCat Java SDK client for evaluating feature flags.

### Usage Example

The following example shows how to use the provider with the OpenFeature SDK.

```java
// Build options for the ConfigCat SDK.
ConfigCatProviderConfig configCatProviderConfig = ConfigCatProviderConfig.builder()
        .sdkKey("#YOUR-SDK-KEY#")
        .options(options -> {
            options.pollingMode(PollingModes.autoPoll());
            options.logLevel(LogLevel.WARNING);
            // ...
        })
        .build();

ConfigCatProvider configCatProvider = new ConfigCatProvider(configCatProviderConfig);

// Configure the provider.
OpenFeatureAPI.getInstance().setProviderAndWait(configCatProvider);

// Create a client.
Client client = OpenFeatureAPI.getInstance().getClient();

// Evaluate your feature flag.
boolean isAwesomeFeatureEnabled = client.getBooleanValue("isAwesomeFeatureEnabled", false);

// With evaluation context.
MutableContext context = new MutableContext();
context.setTargetingKey("#SOME-USER-ID#");
context.add("Email", "configcat@example.com");
context.add("Country", "CountryID");
context.add("Rating", 4.5);

boolean isAwesomeFeatureEnabled = client.getBooleanValue("isAwesomeFeatureEnabled", false, context);
```
For a full list of configuration options see the [ConfigCat Java SDK documentation](https://configcat.com/docs/sdk-reference/java/#creating-the-configcat-client).

## Notes
The underlying ConfigCat Client is accessible via the provider's `getConfigCatClient()` function:

```java
configCatProvider.getConfigCatClient()...
```

## ConfigCat Provider Test Strategy

Unit tests are based on the SDK's [local file override](https://configcat.com/docs/sdk-reference/java/#flag-overrides) feature.  
See [ConfigCatProviderTest.java](./src/test/java/dev/openfeature/contrib/providers/configcat/ConfigCatProviderTest.java)
for more information.
