# flagd Provider for OpenFeature

![Experimental](https://img.shields.io/badge/experimental-breaking%20changes%20allowed-yellow)

A feature flag daemon with a Unix philosophy.

## Installation
<!-- x-release-please-start-version -->
```xml
<dependency>
  <groupId>dev.openfeature.contrib.providers</groupId>
  <artifactId>flagd</artifactId>
  <version>0.5.1</version>
</dependency>
```
<!-- x-release-please-end-version -->

## Usage

The `FlagdProvider` communicates with flagd via the gRPC protocol. Instantiate a new FlagdProvider instance, and configure the OpenFeature SDK to use it:

```java
FlagdProvider provider = new FlagdProvider(Protocol.HTTP, "localhost", 8013);
OpenFeatureAPI.getInstance().setProvider(provider);
```
