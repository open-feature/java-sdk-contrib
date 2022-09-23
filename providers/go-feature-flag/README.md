# GO Feature Flag Java Provider

GO Feature Flag provider allows you to connect to your [GO Feature Flag relay proxy](https://gofeatureflag.org) instance.

## How to use this provider?

To initialize your instance please follow this example:

```java
import dev.openfeature.contrib.providers.gofeatureflag;

// ...
new GoFeatureFlagProvider(
  GoFeatureFlagProviderOptions
  .builder()
  .endpoint("https://my-gofeatureflag-instance.org")
  .timeout(1000)
  .build());
```

You will have a new instance ready to be used with your `open-feature` java SDK.

### Options

| name                     | mandatory | Description                                                                                                    |
|--------------------------|-----------|----------------------------------------------------------------------------------------------------------------|
| **`endpoint`**           | `true`    | endpoint contains the DNS of your GO Feature Flag relay proxy _(ex: https://mydomain.com/gofeatureflagproxy/)_ |
| **`timeout`**            | `false`   | timeout in millisecond we are waiting when calling the go-feature-flag relay proxy API. _(default: 10000)_     |
| **`maxIdleConnections`** | `false`   | maxIdleConnections is the maximum number of connexions in the connexion pool. _(default: 1000)_                |
| **`keepAliveDuration`**  | `false`   | keepAliveDuration is the time in millisecond we keep the connexion open. _(default: 7200000 (2 hours))_        |
