# flagd Provider for OpenFeature

![Experimental](https://img.shields.io/badge/experimental-breaking%20changes%20allowed-yellow)

A feature flag daemon with a Unix philosophy.

This provider is designed to use flagd's [evaluation protocol](https://github.com/open-feature/schemas/blob/main/protobuf/schema/v1/schema.proto), or locally evaluate flags defined in a flagd [flag definition](https://github.com/open-feature/schemas/blob/main/json/flagd-definitions.json).

## Installation
<!-- x-release-please-start-version -->
```xml
<dependency>
  <groupId>dev.openfeature.contrib.providers</groupId>
  <artifactId>flagd</artifactId>
  <version>0.6.2</version>
</dependency>
```
<!-- x-release-please-end-version -->

## Configuration and Usage

The flagd provider can operate in two modes: [RPC](#remote-resolver-rpc) (evaluation takes place in flagd, via gRPC calls) or [in-process](#in-process-resolver) (evaluation takes place in-process, with the provider getting a ruleset from a compliant sync-source).

### Remote resolver (RPC)

This is the default mode of operation of the provider. 
In this mode, `FlagdProvider` communicates with [flagd](https://github.com/open-feature/flagd) via the gRPC protocol.
Flag evaluations take place remotely at the connected flagd instance.

Instantiate a new FlagdProvider instance, and configure the OpenFeature SDK to use it:

```java
// Create a flagd instance with default options
FlagdProvider flagd = new FlagdProvider();
// Set flagd as the OpenFeature Provider
OpenFeatureAPI.getInstance().setProvider(flagd);
```

### In-process resolver

This mode performs flag evaluations locally (in-process). Flag configurations for evaluation are obtained via gRPC protocol using [sync protobuf schema](https://buf.build/open-feature/flagd/file/main:sync/v1/sync_service.proto) service definition.

Consider following example to create a `FlagdProvider` with in-process evaluations,

```java
FlagdProvider flagdProvider = new FlagdProvider(
        FlagdOptions.builder()
                .resolverType(Config.Evaluator.IN_PROCESS)
                .build());
```

In the above example, in-process handlers attempt to connect to a sync service on address `localhost:8013` to obtain [flag definitions](https://github.com/open-feature/schemas/blob/main/json/flagd-definitions.json).

### Configuration options

Options can be defined in the constructor or as environment variables, with constructor options having the highest
precedence.
Default options can be overridden through a `FlagdOptions` based constructor or set to be picked up from the environment
variables.

Given below are the supported configurations. Note that some configurations are only applicable for remote resolver.

| Option name           | Environment variable name      | Type & Values          | Default   | Compatible resolver |
| --------------------- | ------------------------------ | ---------------------- | --------- | ------------------- |
| host                  | FLAGD_HOST                     | String                 | localhost | remote & in-process |
| port                  | FLAGD_PORT                     | int                    | 8013      | remote & in-process |
| tls                   | FLAGD_TLS                      | boolean                | false     | remote & in-process |
| socketPath            | FLAGD_SOCKET_PATH              | String                 | null      | remote & in-process |
| certPath              | FLAGD_SERVER_CERT_PATH         | String                 | null      | remote & in-process |
| deadline              | FLAGD_DEADLINE_MS              | int                    | 500       | remote & in-process |
| cache                 | FLAGD_CACHE                    | String - lru, disabled | lru       | remote              |
| maxCacheSize          | FLAGD_MAX_CACHE_SIZE           | int                    | 1000      | remote              |
| maxEventStreamRetries | FLAGD_MAX_EVENT_STREAM_RETRIES | int                    | 5         | remote              |
| retryBackoffMs        | FLAGD_RETRY_BACKOFF_MS         | int                    | 1000      | remote              |

> [!NOTE]  
> Some configurations are only applicable for RPC resolver.

### Unix socket support

Unix socket communication with flag is facilitated via usage of the linux-native `epoll` library on `linux-x86_64`
only (ARM support is pending relase of `netty-transport-native-epoll` v5). Unix sockets are not supported on other
platforms or architectures.

### Reconnection

Reconnection is supported by the underlying GRPCBlockingStub. If connection to flagd is lost, it will reconnect
automatically.

### Deadline (gRPC call timeout)

The deadline for an individual flag evaluation can be configured by calling `setDeadline(myDeadlineMillis)`.
If the gRPC call is not completed within this deadline, the gRPC call is terminated with the error `DEADLINE_EXCEEDED`
and the evaluation will default.
The default deadline is 500ms, though evaluations typically take on the order of 10ms.
For the in-process provider, the deadline is used when establishing the initial streaming connection.
A failure to connect within this timeout will result an [error event](https://openfeature.dev/docs/reference/concepts/events#provider_error) from the provider, though it will attempt to reconnect indefinitely.

### TLS

Though likely unnecessary in deployments where flagd runs on the same host as the workload, TLS is available.

:warning: Note that there's a [vulnerability](https://security.snyk.io/vuln/SNYK-JAVA-IONETTY-1042268)
in [netty](https://github.com/netty/netty), a transitive dependency of the underlying gRPC libraries used in the
flagd-provider that fails to correctly validate certificates. This will be addressed in netty v5.

### Caching (RPC only)

> [!NOTE]  
> The in-process resolver does not benefit from caching, since all evaluations are done locally and do not involve I/O.

The provider attempts to establish a connection to flagd's event stream (up to 5 times by default).
If the connection is successful and caching is enabled each flag returned with reason `STATIC` is cached until an event is received
concerning the cached flag (at which point it is removed from cache).

On invocation of a flag evaluation (if caching is available) an attempt is made to retrieve the entry from cache, if
found the flag is returned with reason `CACHED`.

By default, the provider is configured to
use [least recently used (lru)](https://commons.apache.org/proper/commons-collections/apidocs/org/apache/commons/collections4/map/LRUMap.html)
caching with up to 1000 entries.

### OpenTelemetry support

OpenTelemetry support can be enabled either through [automatic instrumentation](https://opentelemetry.io/docs/instrumentation/java/automatic/) 
or with [manual instrumentation](https://opentelemetry.io/docs/instrumentation/java/manual/). 

For manual instrumentation, flagd provider can be constructed with an `OpenTelemetry` instance.

```java
FlagdOptions options = 
        FlagdOptions.builder()
                    .openTelemetry(openTelemetry)
                    .build();

FlagdProvider flagdProvider = new FlagdProvider(options);
```

Please refer [OpenTelemetry example](https://opentelemetry.io/docs/instrumentation/java/manual/#example) for best 
practice guideline.

Telemetry configuration combined with [flagd telemetry ](https://github.com/open-feature/flagd/blob/main/docs/configuration/flagd_telemetry.md)
allows distributed tracing.
