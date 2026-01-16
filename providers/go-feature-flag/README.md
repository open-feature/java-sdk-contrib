# GO Feature Flag - OpenFeature Java provider
[![Maven Central Version](https://img.shields.io/maven-central/v/dev.openfeature.contrib.providers/go-feature-flag?color=blue&style=flat-square)](https://search.maven.org/artifact/dev.openfeature.contrib.providers/go-feature-flag)


> [!WARNING]
> This version of the provider requires to use GO Feature Flag relay-proxy `v1.45.0` or above.  
> If you have an older version of the relay-proxy, please use the version `0.4.3` of the provider.

This is the official OpenFeature Java provider for accessing your feature flags with GO Feature Flag.

In conjuction with the [OpenFeature SDK](https://openfeature.dev/docs/reference/concepts/provider) you will be able to evaluate your feature flags in your java/kotlin applications.

For documentation related to flags management in GO Feature Flag, refer to the [GO Feature Flag documentation website](https://gofeatureflag.org/docs).

### Functionalities:

- Manage the integration of the OpenFeature Java SDK and GO Feature Flag relay-proxy.
- 2 types of evaluations available:
    - **In process**: fetch the flag configuration from the GO Feature Flag relay-proxy API and evaluate the flags directly in the provider.
    - **Remote**: Call the GO Feature Flag relay-proxy for each flag evaluation.
- Collect and send evaluation data to the GO Feature Flag relay-proxy for statistics and monitoring purposes.
- Support the OpenFeature [tracking API](https://openfeature.dev/docs/reference/concepts/tracking/) to associate metrics or KPIs with feature flag evaluation contexts.

## Dependency Setup

<!-- x-release-please-start-version -->
```xml
<dependency>
    <groupId>dev.openfeature.contrib.providers</groupId>
    <artifactId>go-feature-flag</artifactId>
    <version>1.1.1</version>
</dependency>
```
<!-- x-release-please-end-version -->

## Getting started
### Initialize the provider
GO Feature Flag provider needs to be created and then set in the global OpenFeatureAPI.

The only required option to create a `GoFeatureFlagProvider` is the endpoint to your GO Feature Flag relay-proxy instance.

```java
import dev.openfeature.contrib.providers.gofeatureflag;
//...

FeatureProvider provider = new GoFeatureFlagProvider(
        GoFeatureFlagProviderOptions.builder()
                .endpoint("https://my-gofeatureflag-instance.org")
                .build());

OpenFeatureAPI.getInstance().setProviderAndWait(provider);
// ...
Client client = OpenFeatureAPI.getInstance().getClient("my-goff-provider");

// targetingKey is mandatory for each evaluation
String targetingKey = "ad0c6f75-f5d6-4b17-b8eb-6c923d8d4698";
EvaluationContext evaluationContext = new ImmutableContext(targetingKey);

// Example of a boolean flag evaluation
FlagEvaluationDetails<Boolean> booleanFlagEvaluation = client.getBooleanValue("bool_targeting_match", false, evaluationContext);
```

The evaluation context is the way for the client to specify contextual data that GO Feature Flag uses to evaluate the feature flags, it allows to define rules on the flag.

The `targetingKey` is mandatory for GO Feature Flag in order to evaluate the feature flag, it could be the id of a user, a session ID or anything you find relevant to use as identifier during the evaluation.

### Configure the provider
You can configure the provider with several options to customize its behavior. The following options are available:


| name                              | mandatory | Description                                                                                                                                                                                                                                                                                                                                                                     |
|-----------------------------------|-----------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **`endpoint`**                    | `true`    | endpoint contains the DNS of your GO Feature Flag relay proxy _(ex: https://mydomain.com/gofeatureflagproxy/)_                                                                                                                                                                                                                                                                  |
| **`evaluationType`**              | `false`   | evaluationType is the type of evaluation you want to use.<ul><li>If you want to have a local evaluation, you should use IN_PROCESS.</li><li>If you want to have an evaluation on the relay-proxy directly, you should use REMOTE.</li></ul>Default: IN_PROCESS<br/>                                                                                                             |
| **`timeout`**                     | `false`   | timeout in millisecond we are waiting when calling the relay proxy API. _(default: `10000`)_                                                                                                                                                                                                                                                                                    |
| **`maxIdleConnections`**          | `false`   | maxIdleConnections is the maximum number of connections in the connection pool. _(default: `1000`)_                                                                                                                                                                                                                                                                             |
| **`keepAliveDuration`**           | `false`   | keepAliveDuration is the time in millisecond we keep the connection open. _(default: `7200000` (2 hours))_                                                                                                                                                                                                                                                                      |
| **`apiKey`**                      | `false`   | If the relay proxy is configured to authenticate the requests, you should provide an API Key to the provider. Please ask the administrator of the relay proxy to provide an API Key. (This feature is available only if you are using GO Feature Flag relay proxy v1.7.0 or above). _(default: null)_                                                                           |
| **`flushIntervalMs`**             | `false`   | interval time we publish statistics collection data to the proxy. The parameter is used only if the cache is enabled, otherwise the collection of the data is done directly when calling the evaluation API. default: `1000` ms                                                                                                                                                 |
| **`maxPendingEvents`**            | `false`   | max pending events aggregated before publishing for collection data to the proxy. When event is added while events collection is full, event is omitted. _(default: `10000`)_                                                                                                                                                                                                   |
| **`disableDataCollection`**       | `false`   | set to true if you don't want to collect the usage of flags retrieved in the cache. _(default: `false`)_                                                                                                                                                                                                                                                                        |
| **`exporterMetadata`**            | `false`   | exporterMetadata is the metadata we send to the GO Feature Flag relay proxy when we report the evaluation data usage.                                                                                                                                                                                                                                                           |
| **`evaluationFlagList`**          | `false`   | If you are using in process evaluation, by default we will load in memory all the flags available in the relay proxy. If you want to limit the number of flags loaded in memory, you can use this parameter. By setting this parameter, you will only load the flags available in the list. <p>If null or empty, all the flags available in the relay proxy will be loaded.</p> |
| **`flagChangePollingIntervalMs`** | `false`   | interval time we poll the proxy to check if the configuration has changed. It is used for the in process evaluation to check if we should refresh our internal cache. default: `120000`                                                                                                                                                                                         |

### Evaluate a feature flag
The OpenFeature client is used to retrieve values for the current `EvaluationContext`. For example, retrieving a boolean value for the flag **"my-flag"**:

```java
Client client = OpenFeatureAPI.getInstance().getClient("my-goff-provider");
FlagEvaluationDetails<Boolean> booleanFlagEvaluation = client.getBooleanValue("bool_targeting_match", false, evaluationContext);
```

GO Feature Flag supports different all OpenFeature supported types of feature flags, it means that you can use all the accessor directly

```java
// Boolean
client.getBooleanValue("my-flag", false, evaluationContext);

// String
client.getStringValue("my-flag", "default", evaluationContext);

// Integer
client.getIntegerValue("my-flag", 1, evaluationContext);

// Double
client.getDoubleValue("my-flag", 1.1, evaluationContext);

// Object
client.getObjectDetails("my-flag",Value.objectToValue(new MutableStructure().add("default", "true")), evaluationContext);
```

## How it works
### In process evaluation
When the provider is configured to use in process evaluation, it will fetch the flag configuration from the GO Feature Flag relay-proxy API and evaluate the flags directly in the provider.

The evaluation is done inside the provider using a webassembly module that is compiled from the GO Feature Flag source code.
The `wasm` module is used to evaluate the flags and the source code is available in the [thomaspoignant/go-feature-flag](https://github.com/thomaspoignant/go-feature-flag/tree/main/wasm) repository.

The provider will call the GO Feature Flag relay-proxy API to fetch the flag configuration and then evaluate the flags using the `wasm` module.

### Remote evaluation
When the provider is configured to use remote evaluation, it will call the GO Feature Flag relay-proxy for each flag evaluation.

It will perform an HTTP request to the GO Feature Flag relay-proxy API with the flag name and the evaluation context for each flag evaluation.
