# Code Readiness Hook

The `codereadiness` hook allows controlling feature flag evaluation based on the version of the application code. 
It does this by comparing the current application version with a required minimum version specified in the flag's metadata. 
If the comparison fails (i.e., the application version is lower than the required version), the hook returns an error, causing the flag evaluation to resolve to its configured default value.

## Installation

```xml
<dependency>
    <groupId>dev.openfeature.contrib.hooks</groupId>
    <artifactId>code-readiness-hook</artifactId>
    <version>0.1.0</version>
</dependency>
```

## Setup

First, import the OpenFeature SDK and the code readiness hook:

```java
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.contrib.hooks.codereadiness.CodeReadinessHook;
```

Then, configure the hook with the current version of the application code and register it:

```java
// currentVersion is the current version of the code, which can be retrieved 
// from environment variables, build properties, or configuration files.
String currentVersion = "1.0.0";

CodeReadinessHook codeReadinessHook = CodeReadinessHook.builder(currentVersion).build();

// Register the hook globally at the OpenFeature API level
OpenFeatureAPI.getInstance().addHooks(codeReadinessHook);
```

## How It Works

1. The hook runs during the **After** phase of flag evaluation.
2. It extracts the metadata associated with the evaluated flag.
3. It looks for a specific metadata key (by default, `minCodeVersion`).
4. If found, it compares the current application version against the required minimum version using the configured comparator (by default, a semver comparison).
5. If the current version is **lower** than the required version, it returns an error. This triggers the OpenFeature SDK's fallback mechanism, returning the flag's **default value** to the caller.

## Options

The behavior of the hook can be customized by passing options to the builder:

### Strict Validation

By default, the hook will **not** fail if the `minCodeVersion` metadata or the current application version is missing. To enforce version validation and return an error when these versions are missing, use `strictValidation(true)`.

```java
CodeReadinessHook codeReadinessHook = CodeReadinessHook.builder("1.0.0")
        .strictValidation(true)
        .build();
```

### Custom Metadata Key

To configure the hook to look for a key other than the default `"minCodeVersion"` in the flag's metadata, use `metadataMinVerKey()`.

```java
CodeReadinessHook codeReadinessHook = CodeReadinessHook.builder("1.0.0")
        .metadataMinVerKey("customMetadataKey")
        .build();
```

### Custom Comparator

By default, the hook performs a standard semver comparison. If the application uses a different versioning scheme (such as date-based versioning, revision numbers, or custom build numbers), a custom comparison interface implementation can be provided using `comparator()`.

```java
import dev.openfeature.contrib.hooks.codereadiness.VersionComparator;

VersionComparator customComparator = (current, required) -> {
    // Custom comparison logic: return true if current is ready/sufficient, 
    // or false if current is lower than required.
    return current.compareTo(required) >= 0;
};

CodeReadinessHook codeReadinessHook = CodeReadinessHook.builder("2026.06.30")
        .comparator(customComparator)
        .build();
```