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
    <version>1.0.0</version> <!--x-release-please-version -->
</dependency>
```

## Interfaces

### Evaluator

The `Evaluator` interface handles flag storage and resolution:

```java
public interface Evaluator {
    // flag store methods
    void setFlags(String flagConfigurationJson) throws FlagStoreException;
    List<String> setFlagsAndGetChangedKeys(String flagConfigurationJson) throws FlagStoreException;
    Map<String, Object> getFlagSetMetadata();

    // flag evaluation methods
    ProviderEvaluation<Boolean> resolveBooleanValue(String flagKey, Boolean defaultValue, EvaluationContext ctx);
    ProviderEvaluation<String> resolveStringValue(String flagKey, String defaultValue, EvaluationContext ctx);
    ProviderEvaluation<Integer> resolveIntegerValue(String flagKey, Integer defaultValue, EvaluationContext ctx);
    ProviderEvaluation<Double> resolveDoubleValue(String flagKey, Double defaultValue, EvaluationContext ctx);
    ProviderEvaluation<Value> resolveObjectValue(String flagKey, Value defaultValue, EvaluationContext ctx);
}
```

## Implementations

- [flagd-core](../flagd-core) - The default implementation using JsonLogic-based targeting
