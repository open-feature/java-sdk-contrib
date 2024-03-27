# Flagsmith OpenFeature Java Provider
![Experimental](https://img.shields.io/badge/experimental-breaking%20changes%20allowed-yellow)
[![Download](https://img.shields.io/maven-central/v/com.flagsmith/flagsmith-java-client)](https://mvnrepository.com/artifact/com.flagsmith/flagsmith-java-client)
Flagsmith provides an all-in-one platform for developing, implementing, and managing your feature flags.

## Installation

<!-- x-release-please-start-version -->

```xml

<dependency>
    <groupId>dev.openfeature.contrib.providers</groupId>
    <artifactId>flagsmith</artifactId>
    <version>0.0.9</version>
</dependency>
```

<!-- x-release-please-end-version -->

## Usage

The `FlagsmithProvider` communicates with Flagsmith using the Flagsmith java sdk. Information on the sdk can be found in
the Flagsmith documentaiton here https://docs.flagsmith.com/clients/server-side. The following code snippet shows how to
initialize the `FlagsmithProvider`:

```java
FlagsmithProviderOptions options = FlagsmithProviderOptions.builder()
    .apiKey("API_KEY")
    .build();

FlagsmithProvider provider = new FlagsmithProvider();
OpenFeatureAPI.getInstance().setProvider(provider);
```

Options can be defined using the FlagsmithProviderOptions builder. Below are all the options:

| Option name | Type    | Default   | Description
| ----------- | ------- | --------- | ---------
| apiKey      | String  |  | Your API Token. Note that this is either the `Environment API` key or the `Server Side SDK Token`
| headers      | HashMap<String, String>  |  | Add custom headers which will be sent with each network request to the Flagsmith API.
| envFlagsCacheKey      | String  |  | Enable in-memory caching for the Flagsmith API.
| expireCacheAfterWriteTimeUnit      | TimeUnit  | TimeUnit.MINUTES | The time unit used for cache expiry after write.
| expireCacheAfterWrite      | int  | -1 | The integer time for cache expiry after write.
| expireCacheAfterAccessTimeUnit      | TimeUnit  | TimeUnit.MINUTES | The time unit used for cache expiry after reading.
| expireCacheAfterAccess      | int  | -1 | The integer time for cache expiry after reading.
| maxCacheSize      | int  | -1 | The maximum size of the cache in MB.
| recordCacheStats      | boolean  | false | Whether cache statistics should be recorded.
| baseUri      | String  | https://edge.api.flagsmith.com/api/v1/ | Override the default Flagsmith API URL if you are self-hosting.
| connectTimeout      | int  | 2000 | The network timeout in milliseconds.
| writeTimeout      | int  | 5000 | The network timeout in milliseconds when writing.
| readTimeout      | int  | 5000 | The network timeout in milliseconds when reading.
| sslSocketFactory      | SSLSocketFactory  |  | Override the sslSocketFactory.
| trustManager      | X509TrustManager  |  | X509TrustManager used when overriding the sslSocketFactory.
| httpInterceptor      | Interceptor  |  | Add a custom HTTP interceptor in the form of an okhttp3.Interceptor object.
| retries      | int  | 3 | Add a custom com.flagsmith.config.Retry object to configure the backoff / retry configuration.
| localEvaluation      | boolean  | false | Controls which mode to run in; local or remote evaluation.
| environmentRefreshIntervalSeconds      | int  | 60 | Set environment refresh rate with polling manager.
| enableAnalytics      | boolean  | false | Controls whether Flag Analytics data is sent to the Flagsmith API
| usingBooleanConfigValue      | boolean  | false | Determines whether to resolve a feature value as a boolean or use the isFeatureEnabled as the flag itself. These values will be false and true respectively.

### Identity flags

In order to use specific identity flags, a targeting key must be provided in the EvaluationContext provided to the flag
evaluation method. An example of this can be seen below:

```java
FlagsmithProviderOptions options = FlagsmithProviderOptions.builder()
    .apiKey("API_KEY")
    .build();

FlagsmithProvider provider = new FlagsmithProvider();
OpenFeatureAPI.getInstance().setProvider(provider);

EvaluationContext evaluationContext = new MutableContext();
evaluationContext.setTargetingKey("my-identity");

Client client = api.getClient();
boolean flag = client.getBooleanValue("key", false);
```