# GCP Provider

An OpenFeature provider that reads feature flags from Google Cloud. Currently supports [Google Cloud Secret Manager](https://cloud.google.com/secret-manager), purpose-built for secrets requiring versioning, rotation, and fine-grained IAM access control.

## Installation

<!-- x-release-please-start-version -->
```xml
<dependency>
    <groupId>dev.openfeature.contrib.providers</groupId>
    <artifactId>gcp</artifactId>
    <version>0.0.1</version>
</dependency>
```
<!-- x-release-please-end-version -->

## Quick Start

```java
import dev.openfeature.contrib.providers.gcp.GcpSecretManagerProvider;
import dev.openfeature.contrib.providers.gcp.GcpSecretManagerProviderOptions;
import dev.openfeature.sdk.OpenFeatureAPI;

GcpSecretManagerProviderOptions options = GcpSecretManagerProviderOptions.builder()
    .projectId("my-gcp-project")
    .build();

OpenFeatureAPI.getInstance().setProvider(new GcpSecretManagerProvider(options));

// Evaluate a boolean flag stored as secret "enable-dark-mode" with value "true"
boolean darkMode = OpenFeatureAPI.getInstance().getClient()
    .getBooleanValue("enable-dark-mode", false);
```

## How It Works

Each feature flag is stored as an individual **secret** in GCP Secret Manager. The flag key maps directly to the secret name (with an optional prefix). The `latest` version is accessed by default.

Supported raw value formats:

| Flag type | Secret value example |
|-----------|---------------------|
| Boolean   | `true` or `false`   |
| Integer   | `42`                |
| Double    | `3.14`              |
| String    | `dark-mode`         |
| Object    | `{"color":"blue","level":3}` |

## Authentication

The provider uses [Application Default Credentials (ADC)](https://cloud.google.com/docs/authentication/provide-credentials-adc) by default. No explicit credentials are required when running on GCP infrastructure (Cloud Run, GKE, Compute Engine) or when `gcloud auth application-default login` has been run locally.

To use explicit credentials:

```java
import com.google.auth.oauth2.ServiceAccountCredentials;
import java.io.FileInputStream;

GoogleCredentials credentials = ServiceAccountCredentials.fromStream(
    new FileInputStream("/path/to/service-account-key.json"));

GcpSecretManagerProviderOptions options = GcpSecretManagerProviderOptions.builder()
    .projectId("my-gcp-project")
    .credentials(credentials)
    .build();
```

## Configuration Options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `projectId` | `String` | *(required)* | GCP project ID that owns the secrets |
| `credentials` | `GoogleCredentials` | `null` (ADC) | Explicit credentials; falls back to Application Default Credentials when null |
| `secretVersion` | `String` | `"latest"` | Secret version to access. Use `"latest"` for the current version or a numeric string (e.g. `"3"`) to pin to a specific version |
| `cacheExpiry` | `Duration` | `5 minutes` | How long fetched secret values are cached before re-fetching from GCP |
| `cacheMaxSize` | `int` | `500` | Maximum number of secret values held in the in-memory cache |
| `secretNamePrefix` | `String` | `null` | Optional prefix prepended to every flag key. E.g. prefix `"ff-"` maps flag `"my-flag"` to secret `"ff-my-flag"` |

## Advanced Usage

### Pinning to a specific secret version

```java
GcpSecretManagerProviderOptions options = GcpSecretManagerProviderOptions.builder()
    .projectId("my-gcp-project")
    .secretVersion("5")  // always use version 5
    .build();
```

### Secret name prefix

```java
GcpSecretManagerProviderOptions options = GcpSecretManagerProviderOptions.builder()
    .projectId("my-gcp-project")
    .secretNamePrefix("feature-flags/")
    .build();
```

### Tuning cache for high-throughput scenarios

Secret Manager has API quotas (10,000 access operations per minute per project). Use a longer `cacheExpiry` to stay within quota.

```java
GcpSecretManagerProviderOptions options = GcpSecretManagerProviderOptions.builder()
    .projectId("my-gcp-project")
    .cacheExpiry(Duration.ofMinutes(10))
    .cacheMaxSize(1000)
    .build();
```

## Running Integration Tests

Integration tests require real GCP credentials and pre-created test secrets.

1. Configure ADC: `gcloud auth application-default login`
2. Create test secrets in your project (see `GcpSecretManagerProviderIntegrationTest` for the required secret names and values)
3. Run:

```bash
GCP_PROJECT_ID=my-project mvn verify -pl providers/gcp -Dgroups=integration
```
