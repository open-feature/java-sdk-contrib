# flagd Provider for OpenFeature

This provider is designed to use flagd's [evaluation protocol](https://github.com/open-feature/schemas/blob/main/protobuf/schema/v1/schema.proto), or locally evaluate flags defined in a flagd [flag definition](https://github.com/open-feature/schemas/blob/main/json/flags.json).

## Installation
<!-- x-release-please-start-version -->
```xml
<dependency>
  <groupId>dev.openfeature.contrib.providers</groupId>
  <artifactId>flagd</artifactId>
  <version>0.8.9</version>
</dependency>
```
<!-- x-release-please-end-version -->

## Configuration and Usage

The flagd provider can operate in two modes: [RPC](#remote-resolver-rpc) (evaluation takes place in flagd, via gRPC calls) or [in-process](#in-process-resolver) (evaluation takes place in-process, with the provider getting a ruleset from a compliant sync-source).

### Remote resolver (RPC)

This is the default mode of operation of the provider. 
In this mode, `FlagdProvider` communicates with [flagd](https://github.com/open-feature/flagd) via the gRPC protocol.
Flag evaluations take place remotely at the connected flagd instance.

Instantiate a new FlagdProvider instance and configure the OpenFeature SDK to use it:

```java
// Create a flagd instance with default options
FlagdProvider flagd = new FlagdProvider();
// Set flagd as the OpenFeature Provider
OpenFeatureAPI.getInstance().setProvider(flagd);
```

### In-process resolver

This mode performs flag evaluations locally (in-process). Flag configurations for evaluation are obtained via gRPC protocol using [sync protobuf schema](https://buf.build/open-feature/flagd/file/main:sync/v1/sync_service.proto) service definition.

Consider the following example to create a `FlagdProvider` with in-process evaluations,

```java
FlagdProvider flagdProvider = new FlagdProvider(
        FlagdOptions.builder()
                .resolverType(Config.Resolver.IN_PROCESS)
                .build());
```

In the above example, in-process handlers attempt to connect to a sync service on address `localhost:8013` to obtain [flag definitions](https://github.com/open-feature/schemas/blob/main/json/flags.json).

#### Sync-metadata

To support the injection of contextual data configured in flagd for in-process evaluation, the provider exposes a `getSyncMetadata` accessor which provides the most recent value returned by the [GetMetadata RPC](https://buf.build/open-feature/flagd/docs/main:flagd.sync.v1#flagd.sync.v1.FlagSyncService.GetMetadata).
The value is updated with every (re)connection to the sync implementation.
This can be used to enrich evaluations with such data.
If the `in-process` mode is not used, and before the provider is ready, the `getSyncMetadata` returns an empty map.

#### Offline mode

In-process resolvers can also work in an offline mode.
To enable this mode, you should provide a valid flag configuration file with the option `offlineFlagSourcePath`.

```java
FlagdProvider flagdProvider = new FlagdProvider(
        FlagdOptions.builder()
                .resolverType(Config.Resolver.IN_PROCESS)
                .offlineFlagSourcePath("PATH")
                .build());
```

Provider will attempt to detect file changes using polling. 
Polling happens at 5 second intervals and this is currently unconfigurable.
This mode is useful for local development, tests and offline applications.

#### Custom Connector 

You can include a custom connector as a configuration option to customize how the in-process resolver fetches flags. 
The custom connector must implement the [Connector interface](https://github.com/open-feature/java-sdk-contrib/blob/main/providers/flagd/src/main/java/dev/openfeature/contrib/providers/flagd/resolver/process/storage/connector/Connector.java).

```java
Connector myCustomConnector = new MyCustomConnector();
FlagdOptions options =
        FlagdOptions.builder()
                .resolverType(Config.Resolver.IN_PROCESS)
                .customConnector(myCustomConnector)
                .build();

FlagdProvider flagdProvider = new FlagdProvider(options);
```

> [!IMPORTANT]
> Note that the in-process resolver can only use a single flag source.
> If multiple sources are configured then only one would be selected based on the following order of preference:
>   1. Custom Connector
>   2. Offline file
>   3. gRPC

### Configuration options

Options can be defined in the constructor or as environment variables, with constructor options having the highest
precedence.
Default options can be overridden through a `FlagdOptions` based constructor or set to be picked up from the environment
variables.

Given below are the supported configurations:

| Option name           | Environment variable name      | Type & Values            | Default   | Compatible resolver |
|-----------------------|--------------------------------|--------------------------|-----------|---------------------|
| resolver              | FLAGD_RESOLVER                 | String - rpc, in-process | rpc       |                     |
| host                  | FLAGD_HOST                     | String                   | localhost | rpc & in-process    |
| port                  | FLAGD_PORT                     | int                      | 8013      | rpc & in-process    |
| tls                   | FLAGD_TLS                      | boolean                  | false     | rpc & in-process    |
| socketPath            | FLAGD_SOCKET_PATH              | String                   | null      | rpc & in-process    |
| certPath              | FLAGD_SERVER_CERT_PATH         | String                   | null      | rpc & in-process    |
| deadline              | FLAGD_DEADLINE_MS              | int                      | 500       | rpc & in-process    |
| keepAliveTime         | FLAGD_KEEP_ALIVE_TIME_MS       | long                     | 0         | rpc & in-process    |
| selector              | FLAGD_SOURCE_SELECTOR          | String                   | null      | in-process          |
| cache                 | FLAGD_CACHE                    | String - lru, disabled   | lru       | rpc                 |
| maxCacheSize          | FLAGD_MAX_CACHE_SIZE           | int                      | 1000      | rpc                 |
| maxEventStreamRetries | FLAGD_MAX_EVENT_STREAM_RETRIES | int                      | 5         | rpc                 |
| retryBackoffMs        | FLAGD_RETRY_BACKOFF_MS         | int                      | 1000      | rpc                 |
| offlineFlagSourcePath | FLAGD_OFFLINE_FLAG_SOURCE_PATH | String                   | null      | in-process          |

> [!NOTE]  
> Some configurations are only applicable for RPC resolver.

### Unix socket support

Unix socket communication with flagd is facilitated by usaging of the linux-native `epoll` library on `linux-x86_64`
only (ARM support is pending the release of `netty-transport-native-epoll` v5). Unix sockets are not supported on other
platforms or architectures.

### Reconnection

Reconnection is supported by the underlying GRPCBlockingStub. If the connection to flagd is lost, it will reconnect
automatically.

### Deadline (gRPC call timeout)

The deadline for an individual flag evaluation can be configured by calling `setDeadline(myDeadlineMillis)`.
If the gRPC call is not completed within this deadline, the gRPC call is terminated with the error `DEADLINE_EXCEEDED`
and the evaluation will default.
The default deadline is 500ms, though evaluations typically take on the order of 10ms.
For the in-process provider, the deadline is used when establishing the initial streaming connection.
A failure to connect within this timeout will result in an [error event](https://openfeature.dev/docs/reference/concepts/events#provider_error) from the provider, though it will attempt to reconnect indefinitely.

### TLS

TLS is available in situations where flagd is running on another host.
You may optionally supply an X.509 certificate in PEM format. Otherwise, the default certificate store will be used.

```java
FlagdProvider flagdProvider = new FlagdProvider(
        FlagdOptions.builder()
                .host("myflagdhost")
                .tls(true)                      // use TLS
                .certPath("etc/cert/ca.crt")    // PEM cert
                .build());
```

> [!WARNING]  
> There's a [vulnerability](https://security.snyk.io/vuln/SNYK-JAVA-IONETTY-1042268) in [netty](https://github.com/netty/netty), a transitive dependency of the underlying gRPC libraries used in the flagd-provider that fails to correctly validate certificates.
> This will be addressed in netty v5.

### Caching (RPC only)

> [!NOTE]  
> The in-process resolver does not benefit from caching since all evaluations are done locally and do not involve I/O.

The provider attempts to establish a connection to flagd's event stream (up to 5 times by default).
If the connection is successful and caching is enabled, each flag returned with the reason `STATIC` is cached until an event is received
concerning the cached flag (at which point it is removed from the cache).

On invocation of a flag evaluation (if caching is available), an attempt is made to retrieve the entry from the cache, if
found the flag is returned with the reason `CACHED`.

By default, the provider is configured to
use [least recently used (lru)](https://commons.apache.org/proper/commons-collections/apidocs/org/apache/commons/collections4/map/LRUMap.html)
caching with up to 1000 entries.

### OpenTelemetry tracing (RPC only)

flagd provider support OpenTelemetry traces for gRPC-backed remote evaluations. 

There are two ways you can configure OpenTelemetry for the provider,

- [Using automatic instrumentation](https://opentelemetry.io/docs/instrumentation/java/automatic/)
- [Using manual instrumentation](https://opentelemetry.io/docs/instrumentation/java/manual/)

When using automatic instrumentation, traces for gRPC will be automatically added by the OpenTelemetry Java library.
These traces, however will not include extra attributes added when using manual instrumentation. 

When using manual instrumentation, you have two options to construct flagd provider to enable traces.

The first(preferred) option is to construct the provider with an OpenTelemetry instance,

```java
FlagdOptions options = 
        FlagdOptions.builder()
                .openTelemetry(openTelemetry)
                .build();

FlagdProvider flagdProvider = new FlagdProvider(options);
```

The second option is useful if you have set up a GlobalOpenTelemetry in your runtime.
You can allow flagd to derive the OpenTelemetry instance by enabling `withGlobalTelemetry` option.

```java
FlagdOptions options =
        FlagdOptions.builder()
            .withGlobalTelemetry(true)
            .build();

FlagdProvider flagdProvider = new FlagdProvider(options);
```

Please refer [OpenTelemetry example](https://opentelemetry.io/docs/instrumentation/java/manual/#example) for best practice guidelines.

Provider telemetry combined with [flagd OpenTelemetry](https://flagd.dev/reference/monitoring/#opentelemetry) allows you to have distributed traces.
