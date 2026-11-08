# OFREP Provider for OpenFeature

This provider allows to connect to any feature flag management system that supports OFREP.

## Installation
For Maven
<!-- x-release-please-start-version -->
```xml
<dependency>
  <groupId>dev.openfeature.contrib.providers</groupId>
  <artifactId>ofrep</artifactId>
  <version>0.0.2</version>
</dependency>
```

For Gradle
```groovy
implementation 'dev.openfeature.contrib.providers:ofrep:0.0.2'
```
<!-- x-release-please-end-version -->

## Configuration and Usage

### Usage
```java
OfrepProviderOptions options = OfrepProviderOptions.builder().build();
OfrepProvider ofrepProvider = OfrepProvider.constructProvider(options);
```
### Example
```java
import dev.openfeature.contrib.providers.ofrep.OfrepProvider;
import dev.openfeature.contrib.providers.ofrep.OfrepProviderOptions;
import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.MutableContext;
import dev.openfeature.sdk.OpenFeatureAPI;

public class App {
    public static void main(String[] args) {
        OpenFeatureAPI openFeatureAPI = OpenFeatureAPI.getInstance();

        OfrepProviderOptions options = OfrepProviderOptions.builder().build();
        OfrepProvider ofrepProvider = OfrepProvider.constructProvider(options);

        openFeatureAPI.setProvider(ofrepProvider);

        Client client = openFeatureAPI.getClient();

        MutableContext context = new MutableContext();
        context.setTargetingKey("my-identify-id");

        FlagEvaluationDetails<Boolean> details = client.getBooleanDetails("my-boolean-flag", false, context);
        System.out.println("Flag value: " + details.getValue());

        openFeatureAPI.shutdown();
    }
}
```

### Configuration options

Options are passed via `OfrepProviderOptions`, using which default values can be overridden.

Given below are the supported configurations:


| Option name | Type    | Default   | Description
| ----------- | ------- | --------- | ---------
| baseUrl      | String  | http://localhost:8016 | Override the default OFREP API URL.
| headers      | ImmutableMap  | Empty Map | Add custom headers which will be sent with each network request to the OFREP API.
| timeout      | Duration  | 10 Seconds | The timeout duration to establishing the connection.
| proxySelector      | ProxySelector  | ProxySelector.getDefault() | The proxy selector used by HTTP Client.
| executor      | Executor  | Thread Pool of size 5 | The executor used by HTTP Client.

### Environment variables

The provider reads the standard OFREP environment variables and uses them as defaults. Values set on `OfrepProviderOptions` take precedence.

| Variable | Option | Example |
| -------- | ------ | ------- |
| `OFREP_ENDPOINT` | baseUrl | `http://localhost:2321` |
| `OFREP_HEADERS` | headers | `Authorization=Bearer%20123,X-Custom=value` |
| `OFREP_TIMEOUT_MS` | requestTimeout | `5000` |

`OFREP_HEADERS` is a comma-separated list of `Key=Value` pairs. The whole value is percent-decoded first, so use `%20` for a space; commas always separate entries, even when encoded as `%2C`. Malformed entries and an invalid `OFREP_TIMEOUT_MS` are logged as warnings and skipped.

```java
// OFREP_ENDPOINT=http://flags.example.com:2321
OfrepProvider ofrepProvider = OfrepProvider.constructProvider(OfrepProviderOptions.builder().build());
```

