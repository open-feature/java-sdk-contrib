# flagd-api

This module contains the API contracts for flagd in-process flag evaluation.

## Purpose

The `flagd-api` module defines the core interfaces that can be implemented by different flag evaluation engines. This allows:

- Publishing the contract separately from implementations
- Creating alternative implementations in other repositories
- Substituting flag evaluation logic without changing consumer code

## Installation

```xml
<dependency>
    <groupId>dev.openfeature.contrib.tools</groupId>
    <artifactId>flagd-api</artifactId>
    <version>0.0.1</version> <!--x-release-please-version -->
</dependency>
```

## Interfaces

### Evaluator

The `Evaluator` interface handles flag resolution:

```java
public interface Evaluator {
    ProviderEvaluation<Boolean> resolveBooleanValue(String flagKey, EvaluationContext ctx);
    ProviderEvaluation<String> resolveStringValue(String flagKey, EvaluationContext ctx);
    ProviderEvaluation<Integer> resolveIntegerValue(String flagKey, EvaluationContext ctx);
    ProviderEvaluation<Double> resolveDoubleValue(String flagKey, EvaluationContext ctx);
    ProviderEvaluation<Value> resolveObjectValue(String flagKey, EvaluationContext ctx);
}
```

### FlagStore

The `FlagStore` interface handles flag configuration storage and updates:

```java
public interface FlagStore {
    void setFlags(String flagConfigurationJson) throws FlagStoreException;
    List<String> setFlagsAndGetChangedKeys(String flagConfigurationJson) throws FlagStoreException;
    Map<String, Object> getFlagSetMetadata();
}
```

## Implementations

- [flagd-core](../flagd-core) - The default implementation using JsonLogic-based targeting
