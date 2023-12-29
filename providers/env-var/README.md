# Environment Variables Java Provider

Environment Variables provider allows you to read feature flags from the [process's environment](https://en.wikipedia.org/wiki/Environment_variable).

## Installation

<!-- x-release-please-start-version -->

```xml
<dependency>
    <groupId>dev.openfeature.contrib.providers</groupId>
    <artifactId>env-var</artifactId>
    <version>0.0.5</version>
</dependency>
```

<!-- x-release-please-end-version -->

## Usage

To use the `EnvVarProvider` create an instance and use it as a provider:

```java
    EnvVarProvider provider = new EnvVarProvider();
    OpenFeatureAPI.getInstance().setProvider(provider);
```

### Configuring different methods of fetching environment variables

This provider defines an `EnvironmentGateway` interface, which is used to access the actual environment variables. 
The class [OS][os-class] is implementing this interface and creates the actual connection between provider 
and environment variables. For testing or in case accessing the environment variables is more complex than supported
by Java's `java.lang.System` class, the implementation can be switched accordingly by passing it into the constructor 
of the provider.

```java
    EnvironmentGateway testFake = arg -> "true"; //always returns true
    
    EnvVarProvider provider = new EnvVarProvider(testFake);
    OpenFeatureAPI.getInstance().setProvider(provider);
```

### Key Transformation

This provider supports transformation of keys to support different patterns used for naming feature flags and for
naming environment variables, e.g. SCREAMING_SNAKE_CASE env variables vs. hyphen-case keys for feature flags.
It supports chaining/combining different transformers incl. self-written ones by providing a transforming function in the constructor.
Currently, the following transformations are supported out of the box:

- converting to lower case (e.g. `Feature.Flag` => `feature.flag`)
- converting to UPPER CASE (e.g. `Feature.Flag` => `FEATURE.FLAG`)
- converting hyphen-case to SCREAMING_SNAKE_CASE (e.g. `Feature-Flag` => `FEATURE_FLAG`)
- convert to camelCase (e.g. `FEATURE_FLAG` => `featureFlag`)
- replace '_' with '.' (e.g. `feature_flag` => `feature.flag`)
- replace '.' with '_' (e.g. `feature.flag` => `feature_flag`)

**Examples:**

1. hyphen-case feature flag names to screaming snake-case environment variables:

```java
    // Definition of the EnvVarProvider:
    FeatureProvider provider = new EnvVarProvider(EnvironmentKeyTransformer.hyphenCaseToScreamingSnake());
```

2. chained/composed transformations:

```java
    // Definition of the EnvVarProvider:
    EnvironmentKeyTransformer keyTransformer = EnvironmentKeyTransformer
        .toLowerCaseTransformer()
        .andThen(EnvironmentKeyTransformer.replaceUnderscoreWithDotTransformer());

    FeatureProvider provider = new EnvVarProvider(keyTransformer);
```

3. freely defined transformation function:

```java
    // Definition of the EnvVarProvider:   
    EnvironmentKeyTransformer keyTransformer = new EnvironmentKeyTransformer(key -> key.substring(1));
    FeatureProvider provider = new EnvVarProvider(keyTransformer);
```

<!-- links -->

[os-class]: src/main/java/dev/openfeature/contrib/providers/envvar/OS.java
