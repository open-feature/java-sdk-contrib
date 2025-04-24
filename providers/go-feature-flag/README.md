# GO Feature Flag Java Provider

GO Feature Flag provider allows you to connect to your [GO Feature Flag relay proxy](https://gofeatureflag.org) instance.

## How to use this provider?

To use your instance please follow this example:

```java
import dev.openfeature.contrib.providers.gofeatureflag;

// ...
FeatureProvider provider = new GoFeatureFlagProvider(
  GoFeatureFlagProviderOptions
  .builder()
  .endpoint("https://my-gofeatureflag-instance.org")
  .timeout(1000)
  .build());

OpenFeatureAPI.getInstance().setProviderAndWait(provider);

// ...

Client client = OpenFeatureAPI.getInstance().getClient("my-provider");

// targetingKey is mandatory for each evaluation
String targetingKey = "ad0c6f75-f5d6-4b17-b8eb-6c923d8d4698";
EvaluationContext evaluationContext = new ImmutableContext(targetingKey);

FlagEvaluationDetails<Boolean> booleanFlagEvaluationDetails = client.getBooleanDetails("feature_flag1", false, evaluationContext);
Boolean value = booleanFlagEvaluationDetails.getValue();

// ...
        
provider.shutdown();
```

You will have a new instance ready to be used with your `open-feature` java SDK.

### Options

| name                              | mandatory | Description                                                                                                                                                                                                                                                                                           |
|-----------------------------------|-----------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **`endpoint`**                    | `true`    | endpoint contains the DNS of your GO Feature Flag relay proxy _(ex: https://mydomain.com/gofeatureflagproxy/)_                                                                                                                                                                                        |
| **`timeout`**                     | `false`   | timeout in millisecond we are waiting when calling the go-feature-flag relay proxy API. _(default: 10000)_                                                                                                                                                                                            |
| **`maxIdleConnections`**          | `false`   | maxIdleConnections is the maximum number of connexions in the connexion pool. _(default: 1000)_                                                                                                                                                                                                       |
| **`keepAliveDuration`**           | `false`   | keepAliveDuration is the time in millisecond we keep the connexion open. _(default: 7200000 (2 hours))_                                                                                                                                                                                               |
| **`apiKey`**                      | `false`   | If the relay proxy is configured to authenticate the requests, you should provide an API Key to the provider. Please ask the administrator of the relay proxy to provide an API Key. (This feature is available only if you are using GO Feature Flag relay proxy v1.7.0 or above). _(default: null)_ |
| **`enableCache`**                 | `false`   | enable cache value. _(default: true)_                                                                                                                                                                                                                                                                 |
| **`cacheConfig`**                 | `false`   | If cache custom configuration is wanted, you should provide a [Caffeine](https://github.com/ben-manes/caffeine) configuration object. _(default: null)_                                                                                                                                                                                                        |
| **`flushIntervalMs`**             | `false`   | interval time we publish statistics collection data to the proxy. The parameter is used only if the cache is enabled, otherwise the collection of the data is done directly when calling the evaluation API. _(default: 1000 ms)_                                                                     |
| **`maxPendingEvents`**            | `false`   | max pending events aggregated before publishing for collection data to the proxy. When event is added while events collection is full, event is omitted. _(default: 10000)_                                                                                                                           |
| **`flagChangePollingIntervalMs`** | `false`   | interval time we poll the proxy to check if the configuration has changed.<br/>If the cache is enabled, we will poll the relay-proxy every X milliseconds to check if the configuration has changed. _(default: 120000)_                                                                              |
| **`disableDataCollection`**       | `false`   | set to true if you don't want to collect the usage of flags retrieved in the cache. _(default: false)_                                                                                                                                                                                                |

## Breaking changes

### 0.4.0 - Cache Implementation Change: Guava to Caffeine

In this release, we have updated the cache implementation from Guava to Caffeine. This change was made because Caffeine is now the recommended caching solution by the maintainers of Guava due to its performance improvements and enhanced features.

Because of this, the cache configuration on `GoFeatureFlagProviderOptions` that used Guava's `CacheBuilder` is now handled by `Caffeine`.

#### How to migrate

Configuration cache with Guava used to be like this:

```java
import com.google.common.cache.CacheBuilder;
// ...
CacheBuilder guavaCacheBuilder = CacheBuilder.newBuilder()
  .initialCapacity(100)
  .maximumSize(2000);

FeatureProvider provider = new GoFeatureFlagProvider(
  GoFeatureFlagProviderOptions
  .builder()
  .endpoint("https://my-gofeatureflag-instance.org")
  .cacheBuilder(guavaCacheBuilder)
  .build());

OpenFeatureAPI.getInstance().setProviderAndWait(provider);

// ...
```

Now with Caffeine it should be like this:

```java
import com.github.benmanes.caffeine.cache.Caffeine;
// ...
Caffeine caffeineCacheConfig = Caffeine.newBuilder()
  .initialCapacity(100)
  .maximumSize(2000);

FeatureProvider provider = new GoFeatureFlagProvider(
  GoFeatureFlagProviderOptions
  .builder()
  .endpoint("https://my-gofeatureflag-instance.org")
  .cacheConfig(caffeineCacheConfig)
  .build());

OpenFeatureAPI.getInstance().setProviderAndWait(provider);

// ...
```

For a complete list of customizations  options available in Caffeine, please refer to the [Caffeine documentation](https://github.com/ben-manes/caffeine/wiki) for more details.