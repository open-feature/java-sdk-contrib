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
        <version>1.0.3</version>
    </dependency>
```
<!-- x-release-please-end-version -->

## Usage

OpenFeature provider various ways to register hooks. The location that a hook is registered affects when the hook is
run. It's recommended to register the `OpenTelemetryHook` globally in most situations, but it's possible to only enable
the hook on specific clients. You should **never** register the `OpenTelemetryHook` both globally and on a client.

Consider following code example for usage,

```java
    final Tracer tracer = ... // derive Tracer from OpenTelemetry
    
    // Set OpenTelemetry hook globally
    OpenFeatureAPI.getInstance().addHooks(new OpenTelemetryHook());

    // OpenFeature client
    final Client client = api.getClient();    
     
    // Derive span from OTEL tracer
    Span span = tracer.spanBuilder("boolEvalLogic").startSpan();
    
    // flag evaluation - Hook's span is derived from current context
    try(Scope ignored =  span.makeCurrent()) {
        final Boolean boolEval = client.getBooleanValue(FLAG_KEY, false);
    } finally {
        span.end();
    }
```


### Options

Options can be provided through `OpenTelemetryHookOptions` based constructor.

- setErrorStatus: Control Span error status. Default is true - Span status is set to Error if an error occurs.