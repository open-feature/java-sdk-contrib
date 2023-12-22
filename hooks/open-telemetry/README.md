# OpenTelemetry Hooks

The OpenTelemetry hooks for OpenFeature provides [OTel compliant](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/feature-flags.md) hooks for traces and metrics.
These hooks can be used to determine the impact a feature has on a request, enabling enhanced observability use cases, such as A/B testing or progressive feature releases.

## Installation

<!-- x-release-please-start-version -->

```xml

<dependency>
    <groupId>dev.openfeature.contrib.hooks</groupId>
    <artifactId>otel</artifactId>
    <version>3.1.1</version>
</dependency>
```

<!-- x-release-please-end-version -->

## Usage

OpenFeature provides various ways to register hooks. The location _where_ a hook is registered affects _when_ the hook is executed.
It's recommended to register the hooks globally in most situations, but it's possible to only enable the hook on specific clients.

> You should **never** register the OpenTelemetry hooks both globally and on a client.

### TracesHook

`TracesHook` provides OTel traces and export [span events](https://opentelemetry.io/docs/concepts/signals/traces/#span-events) for each feature flag evaluation.
The hook implementation is attached to `after`and `error` [hook stages](https://github.com/open-feature/spec/blob/main/specification/sections/04-hooks.md#overview).

Both successful and failed flag evaluations will add a span event named `feature_flag` with evaluation details such as flag key, provider name and variant.
Failed evaluations can be allowed to set span status to `ERROR`. You can configure this behavior through`TracesHookOptions`.

#### Custom dimensions (attributes)

You can write your own logic to extract custom dimensions from [flag evaluation metadata](https://github.com/open-feature/spec/blob/main/specification/types.md#flag-metadata) by setting a callback to `dimensionExtractor`.
These extracted dimensions will be added to successful flag evaluation spans.

```java
 TracesHookOptions options = TracesHookOptions.builder()
             // configure a callback
            .dimensionExtractor(metadata -> Attributes.builder()
                    .put("boolean", metadata.getBoolean("boolean"))
                    .put("integer", metadata.getInteger("integer"))
                    .build()
            ).build();

TracesHook tracesHook = new TracesHook(options);
```

Alternatively, you can add dimensions at hook construction time using builder option `extraAttributes`.
These extracted dimensions will be added to successful flag evaluation spans as well as flag evaluation error spans.

```java
TracesHookOptions options = TracesHookOptions.builder()
            .extraAttributes(Attributes.builder()
                .put("scope", "my-app")
                .put("region", "us-east-1")
                .build())
            .build();

TracesHook tracesHook = new TracesHook(options);
```

#### Example

Consider the following code example for a complete usage,

```java
final Tracer tracer=... // derive Tracer from OpenTelemetry

// Set TracesHook globally
OpenFeatureAPI.getInstance().addHooks(new TracesHook());

// OpenFeature client
final Client client=api.getClient();

// Derive span from OTEL tracer
Span span=tracer.spanBuilder("boolEvalLogic").startSpan();

// Feature flag evaluation - Hook's span is derived from current context
try(Scope ignored=span.makeCurrent()){
    final Boolean boolEval=client.getBooleanValue(FLAG_KEY,false);
}finally{
    span.end();
}
```

### MetricsHook

`MetricsHook` performs metric collection by tapping into various hook stages.

Below are the metrics extracted by this hook and dimensions they carry:

| Metric key                             | Description                     | Unit         | Dimensions                                               |
|----------------------------------------|---------------------------------|--------------|----------------------------------------------------------|
| feature_flag.evaluation_requests_total | Number of evaluation requests   | {request}    | key & provider name                                      |
| feature_flag.evaluation_success_total  | Flag evaluation successes       | {impression} | key, provider name, reason, variant & custom dimensions* |
| feature_flag.evaluation_error_total    | Flag evaluation errors          | Counter      | key, provider name                                       |
| feature_flag.evaluation_active_count   | Active flag evaluations counter | Counter      | key                                                      |

Consider the following code example for usage,

```java
final OpenTelemetry openTelemetry = ... // OpenTelemetry API instance

// Register MetricsHook globally         
OpenFeatureAPI api = OpenFeatureAPI.getInstance();
api.addHooks(new MetricsHook(openTelemetry));
```

#### Custom dimensions (attributes)

You can extract dimension from `ImmutableMetadata` of the `FlagEvaluationDetails` and add them to `feature_flag. evaluation_success_total` metric.
To use this feature, construct the `MetricsHook` with a list of `DimensionDescription`. 
You can add dimensions of type `String`, `Integer`, `Long`, `Float`, `Double` & `Boolean`.

```java
List<DimensionDescription> customDimensions = new ArrayList<>();
customDimensions.add(new DimensionDescription("metadataA", String.class));
customDimensions.add(new DimensionDescription("metadataB", Integer.class));


OpenFeatureAPI api = OpenFeatureAPI.getInstance();

// Register MetricsHook with custom dimensions
api.addHooks(new MetricsHook(openTelemetry, customDimensions));
```

You can also wrtie your own extraction logic against [flag evaluation metadata](https://github.com/open-feature/spec/blob/main/specification/types.md#flag-metadata) by providing a callback to `attributeSetter`.

```java
final OpenTelemetry openTelemetry = ... // OpenTelemetry API instance

MetricHookOptions hookOptions = MetricHookOptions.builder()
        // configure a callback
        .attributeSetter(metadata -> Attributes.builder()
            .put("boolean", metadata.getBoolean("boolean"))
            .put("integer", metadata.getInteger("integer"))
            .build())
        .build();

final MetricsHook metricHook = new MetricsHook(openTelemetry, hookOptions);
```
Alternatively, you can add dimensions at hook construction time using builder option `extraAttributes`.
Dimensions added through `extraAttributes` option will be included in both flag evaluation success and evaluation error metrics.

```java
MetricHookOptions hookOptions = MetricHookOptions.builder()
        .extraAttributes(Attributes.builder()
            .put("scope", "my-app")
            .put("region", "us-east-1")
            .build())
        .build();

final MetricsHook metricHook = new MetricsHook(openTelemetry, hookOptions);
```