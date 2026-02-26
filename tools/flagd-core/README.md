# flagd-core

flagd-core contains the core logic for flagd [in-process evaluation](https://flagd.dev/architecture/#in-process-evaluation).
This package is intended to be used by concrete implementations of flagd in-process providers.

This module implements the [`Evaluator`](../flagd-api) interface from the `flagd-api` module.

## Usage

flagd-core wraps a simple flagd feature flag storage and flag evaluation logic.

To use this implementation, instantiate a `FlagdCore` and provide valid flagd flag configurations.

```java
FlagdCore core = new FlagdCore();
core.setFlags(FLAG_CONFIGURATION_STRING);
```

Once initialization is complete, use matching flag resolving call:

```java
ProviderEvaluation<Boolean> result = core.resolveBooleanValue("myBoolFlag", evaluationContext);
```

## Installation

```xml
<dependency>
    <groupId>dev.openfeature.contrib.tools</groupId>
    <artifactId>flagd-core</artifactId>
    <version>0.0.2</version> <!--x-release-please-version -->
</dependency>
```

## Interface

The core component implements the `Evaluator` interface from [flagd-api](../flagd-api):

```java
public interface Evaluator {
    ProviderEvaluation<Boolean> resolveBooleanValue(String flagKey, EvaluationContext ctx);
    ProviderEvaluation<String> resolveStringValue(String flagKey, EvaluationContext ctx);
    ProviderEvaluation<Integer> resolveIntegerValue(String flagKey, EvaluationContext ctx);
    ProviderEvaluation<Double> resolveDoubleValue(String flagKey, EvaluationContext ctx);
    ProviderEvaluation<Value> resolveObjectValue(String flagKey, EvaluationContext ctx);
}
```
