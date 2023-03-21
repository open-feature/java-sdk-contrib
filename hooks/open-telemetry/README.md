# OpenTelemetry Hook

The OpenTelemetry hook for OpenFeature provides
a [spec compliant] (https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/feature-flags.md)
way to automatically add a feature flag
evaluation to a span as a span event. This can be used to determine the impact a feature has on a request,
enabling enhanced observability use cases, such as A/B testing or progressive feature releases.

## Installation
<!-- x-release-please-start-version -->
```xml
    <dependency>
        <groupId>dev.openfeature.contrib.hooks</groupId>
        <artifactId>otel</artifactId>
        <version>1.0.2</version>
    </dependency>
```
<!-- x-release-please-end-version -->

## Usage

OpenFeature provider various ways to register hooks. The location that a hook is registered affects when the hook is
run. It's recommended to register the `OpenTelemetryHook` globally in most situations, but it's possible to only enable
the hook on specific clients. You should **never** register the `OpenTelemetryHook` globally and on a client.

```
     OpenFeatureAPI.getInstance().addHooks(new OpenTelemetryHook());
```
