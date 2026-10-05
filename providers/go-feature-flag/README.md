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
    <version>2.0.0</version>
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


| name                              | mandatory | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
|-----------------------------------|-----------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **`endpoint`**                    | `true`    | endpoint contains the DNS of your GO Feature Flag relay proxy _(ex: https://mydomain.com/gofeatureflagproxy/)_                                                                                                                                                                                                                                                                                                                                                                                        |
| **`evaluationType`**              | `false`   | evaluationType is the type of evaluation you want to use.<ul><li>If you want to have a local evaluation, you should use IN_PROCESS.</li><li>If you want to have an evaluation on the relay-proxy directly, you should use REMOTE.</li></ul>Default: IN_PROCESS<br/>                                                                                                                                                                                                                                   |
| **`timeout`**                     | `false`   | timeout in millisecond we are waiting when calling the relay proxy API. _(default: `10000`)_                                                                                                                                                                                                                                                                                                                                                                                                          |
| **`apiKey`**                      | `false`   | If the relay proxy is configured to authenticate the requests, you should provide an API Key to the provider. Please ask the administrator of the relay proxy to provide an API Key. (This feature is available only if you are using GO Feature Flag relay proxy v1.7.0 or above). _(default: null)_                                                                                                                                                                                                 |
| **`dataCollectorBaseUrl`**        | `false`   | base URL used to publish the evaluation and tracking events, when the data collector is not served by the relay proxy itself. It replaces the whole base of the collector route, scheme, host, port and path prefix included; the flag configuration and the evaluations keep using `endpoint`. _(default: `endpoint`)_                                                                                                                                                       |
| **`customHeaders`**               | `false`   | Extra HTTP headers added to every request the provider makes (flag configuration, evaluation and data collection, including `dataCollectorBaseUrl`), for deployments behind a gateway that needs its own authentication. A configured `apiKey` always wins over a custom `X-API-Key`. `Content-Type` and `If-None-Match` are set by the provider and refused here, as are the headers the Java HTTP client restricts (`Host`, `Connection`, `Content-Length`, `Expect`, `Upgrade`). _(default: none)_ |
| **`flushIntervalMs`**             | `false`   | interval time in millisecond we publish the collected evaluation and tracking events to the data collector. _(default: `60000` (1 minute))_                                                                                                                                                                                                                                                                                                                                                           |
| **`maxPendingEvents`**            | `false`   | max pending events aggregated before publishing for collection data to the proxy. Once that many events are pending they are published without waiting for `flushIntervalMs`. If they cannot be published, at most twice that many are kept and the oldest are dropped. _(default: `10000`)_                                                                                                                                                                                                          |
| **`disableDataCollection`**       | `false`   | set to true if you don't want to send the evaluation and tracking events to the data collector. _(default: `false`)_                                                                                                                                                                                                                                                                                                                                                                                  |
| **`exporterMetadata`**            | `false`   | exporterMetadata is the metadata we send to the GO Feature Flag relay proxy when we report the evaluation data usage.                                                                                                                                                                                                                                                                                                                                                                                 |
| **`evaluationFlagList`**          | `false`   | If you are using in process evaluation, by default we will load in memory all the flags available in the relay proxy. If you want to limit the number of flags loaded in memory, you can use this parameter. By setting this parameter, you will only load the flags available in the list. <p>If null or empty, all the flags available in the relay proxy will be loaded.</p>                                                                                                                       |
| **`flagChangePollingIntervalMs`** | `false`   | interval time we poll the proxy to check if the configuration has changed. It is used for the in process evaluation to check if the flag configuration it holds should be refreshed. Each poll is randomly shortened or lengthened by up to 10%, so that providers started together do not poll in lockstep. default: `120000`                                                                                                                                                                                       |
| **`wasmEvaluatorPoolSize`**       | `false`   | _(IN_PROCESS only)_ Number of WASM instances kept in the evaluation pool. Each instance owns independent memory, allowing fully concurrent flag evaluations without serialisation. Each instance holds about 2.3 MiB of memory once warm. Must be `>= 1`. _(default: number of available CPU cores)_                                                                                                                                                                                                                                                         |

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
The `wasm` module is used to evaluate the flags and the source code is available in the [thomaspoignant/go-feature-flag](https://github.com/thomaspoignant/go-feature-flag/tree/main/cmd/wasm) repository.

The provider will call the GO Feature Flag relay-proxy API to fetch the flag configuration and then evaluate the flags using the `wasm` module.

The `wasm` module is compiled into Java bytecode when the provider is built, so it ships as classes inside the provider jar. There is no `.wasm` file to locate at runtime, and repackaging the provider (shaded or fat jars) keeps the engine with it. The engine version is pinned by the provider release.

#### Performance
Some of the engine's functions compile to Java methods larger than the JVM's default JIT limit (`HugeMethodLimit`, 8000 bytes of bytecode), and HotSpot leaves such methods to the bytecode interpreter. This includes the evaluation path itself, so the engine runs about 3x slower than it could: roughly 350 µs instead of 110 µs per evaluation on an Apple M4 Pro with JDK 21.

If this matters to you, start the JVM with `-XX:-DontCompileHugeMethods`. The flag is process-wide and lifts the limit for every class, not only the engine's.

### Remote evaluation
When the provider is configured to use remote evaluation, it will call the GO Feature Flag relay-proxy for each flag evaluation.

It will perform an HTTP request to the GO Feature Flag relay-proxy API with the flag name and the evaluation context for each flag evaluation.

### Logging
The provider logs through [SLF4J](https://www.slf4j.org/), so its diagnostics go wherever your SLF4J backend sends them. Every logger it uses sits under `dev.openfeature.contrib.providers.gofeatureflag`, and remote evaluation also logs under `dev.openfeature.contrib.providers.ofrep`.

What the evaluation engine prints, such as the panic it reports before a trap, is logged too, at error level by `dev.openfeature.contrib.providers.gofeatureflag.wasm.EvaluationWasm`, rather than written to the process's standard streams.
