# GCP Parameter Manager Provider

An OpenFeature provider that reads feature flags from [Google Cloud Parameter Manager](https://cloud.google.com/secret-manager/parameter-manager/docs/overview), the GCP-native equivalent of AWS SSM Parameter Store.

## Installation

<!-- x-release-please-start-version -->
```xml
<dependency>
    <groupId>dev.openfeature.contrib.providers</groupId>
    <artifactId>gcp-parameter-manager</artifactId>
    <version>0.0.1</version>
</dependency>
```
<!-- x-release-please-end-version -->

## Quick Start

```java
import dev.openfeature.contrib.providers.gcpparametermanager.GcpParameterManagerProvider;
import dev.openfeature.contrib.providers.gcpparametermanager.GcpParameterManagerProviderOptions;
import dev.openfeature.sdk.OpenFeatureAPI;

GcpParameterManagerProviderOptions options = GcpParameterManagerProviderOptions.builder()
    .projectId("my-gcp-project")
    .build();

OpenFeatureAPI.getInstance().setProvider(new GcpParameterManagerProvider(options));

// Evaluate a boolean flag stored in GCP as parameter "enable-dark-mode"
boolean darkMode = OpenFeatureAPI.getInstance().getClient()
    .getBooleanValue("enable-dark-mode", false);
```

## How It Works

Each feature flag is stored as an individual **parameter** in GCP Parameter Manager. The flag key maps directly to the parameter name (with an optional prefix).

Supported raw value formats:

| Flag type | Parameter value example |
|-----------|------------------------|
| Boolean   | `true` or `false`      |
| Integer   | `42`                   |
| Double    | `3.14`                 |
| String    | `dark-mode`            |
| Object    | `{"color":"blue","level":3}` |

## Authentication

The provider uses [Application Default Credentials (ADC)](https://cloud.google.com/docs/authentication/provide-credentials-adc) by default. No explicit credentials are required when running on GCP infrastructure (Cloud Run, GKE, Compute Engine) or when `gcloud auth application-default login` has been run locally.

To use explicit credentials:

```java
import com.google.auth.oauth2.ServiceAccountCredentials;
import java.io.FileInputStream;

GoogleCredentials credentials = ServiceAccountCredentials.fromStream(
    new FileInputStream("/path/to/service-account-key.json"));

GcpParameterManagerProviderOptions options = GcpParameterManagerProviderOptions.builder()
    .projectId("my-gcp-project")
    .credentials(credentials)
    .build();
```

## Configuration Options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `projectId` | `String` | *(required)* | GCP project ID that owns the parameters |
| `locationId` | `String` | `"global"` | GCP location for the Parameter Manager endpoint (`"global"` or a region such as `"us-central1"`) |
| `credentials` | `GoogleCredentials` | `null` (ADC) | Explicit credentials; falls back to Application Default Credentials when null |
| `cacheExpiry` | `Duration` | `5 minutes` | How long fetched values are cached before re-fetching from GCP |
| `cacheMaxSize` | `int` | `500` | Maximum number of flag values held in the in-memory cache |
| `parameterNamePrefix` | `String` | `null` | Optional prefix prepended to every flag key. E.g. prefix `"ff-"` maps flag `"my-flag"` to parameter `"ff-my-flag"` |

## Advanced Usage

### Regional endpoint

```java
GcpParameterManagerProviderOptions options = GcpParameterManagerProviderOptions.builder()
    .projectId("my-gcp-project")
    .locationId("us-central1")
    .build();
```

### Parameter name prefix

```java
GcpParameterManagerProviderOptions options = GcpParameterManagerProviderOptions.builder()
    .projectId("my-gcp-project")
    .parameterNamePrefix("feature-flags/")
    .build();
```

### Tuning cache for high-throughput scenarios

GCP Parameter Manager has API quotas. Use a longer `cacheExpiry` to reduce quota consumption.

```java
GcpParameterManagerProviderOptions options = GcpParameterManagerProviderOptions.builder()
    .projectId("my-gcp-project")
    .cacheExpiry(Duration.ofMinutes(10))
    .cacheMaxSize(1000)
    .build();
```

## Running Integration Tests

Integration tests require real GCP credentials and pre-created test parameters.

1. Configure ADC: `gcloud auth application-default login`
2. Create test parameters in your project (see `GcpParameterManagerProviderIntegrationTest` for the required parameter names)
3. Run:

```bash
GCP_PROJECT_ID=my-project mvn verify -pl providers/gcp-parameter-manager -Dgroups=integration
```
