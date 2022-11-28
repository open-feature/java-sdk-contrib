# flagd Provider for OpenFeature

![Experimental](https://img.shields.io/badge/experimental-breaking%20changes%20allowed-yellow)

A feature flag daemon with a Unix philosophy.

## Installation
<!-- x-release-please-start-version -->
```xml
<dependency>
  <groupId>dev.openfeature.contrib.providers</groupId>
  <artifactId>flagd</artifactId>
  <version>0.5.3</version>
</dependency>
```
<!-- x-release-please-end-version -->

## Usage

The `FlagdProvider` communicates with flagd via the gRPC protocol. Instantiate a new FlagdProvider instance, and configure the OpenFeature SDK to use it:

```java
FlagdProvider provider = new FlagdProvider();
OpenFeatureAPI.getInstance().setProvider(provider);
```

Options can be defined in the constructor or as environment variables, with constructor options having the highest precedence.

| Option name | Environment variable name | Type    | Default   |
| ----------- | ------------------------- | ------- | --------- |
| host        | FLAGD_HOST                | string  | localhost |
| port        | FLAGD_PORT                | number  | 8013      |
| tls         | FLAGD_TLS                 | boolean | false     |
| socketPath  | FLAGD_SOCKET_PATH         | string  | -         |
| certPath    | FLAGD_SERVER_CERT_PATH    | string  | -         |

### Unix socket support

Unix socket communication with flag is facilitated via usage of the linux-native `epoll` library on `linux-x86_64` only (ARM support is pending relase of `netty-transport-native-epoll` v5). Unix sockets are not supported on other platforms or architectures.

### Reconnection

Reconnection is supported by the underlying GRPCBlockingStub. If connection to flagd is lost, it will reconnect automatically.

### Deadline (gRPC call timeout)

The deadline for an individual flag evaluation can be configured by calling `setDeadline(< deadline in millis >)`.
If the gRPC call is not completed within this deadline, the gRPC call is terminated with the error `DEADLINE_EXCEEDED` and the evaluation will default.
The default deadline is 500ms, though evaluations typically take on the order of 10ms.

### TLS

Though not required in deployments where flagd runs on the same host as the workload, TLS is available.

:warning: Note that there's a [vulnerability](https://security.snyk.io/vuln/SNYK-JAVA-IONETTY-1042268) in [netty](https://github.com/netty/netty), a transitive dependency of the underlying gRPC libraries used in the flagd-provider that fails to correctly validate certificates. This will be addressed in netty v5.
