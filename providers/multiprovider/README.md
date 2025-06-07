# OpenFeature Multi-Provider for Java

The OpenFeature Multi-Provider wraps multiple underlying providers in a unified interface, allowing the SDK client to transparently interact with all those providers at once.
This allows use cases where a single client and evaluation interface is desired, but where the flag data should come from more than one source.

Some examples:

- A migration from one feature flagging provider to another.
  During that process, you may have some flags that have been ported to the new system and others that haven’t.
  Therefore, you’d want the Multi-Provider to return the result of the “new” system if available otherwise, return the "old" system’s result.
- Long-term use of multiple sources for flags.
  For example, someone might want to be able to combine environment variables, database entries, and vendor feature flag results together in a single interface, and define the precedence order in which those sources should be consulted.
- Setting a fallback for cloud providers.
  You can use the Multi-Provider to automatically fall back to a local configuration if an external vendor provider goes down, rather than using the default values.
  By using the FirstSuccessfulStrategy, the Multi-Provider will move on to the next provider in the list if an error is thrown.

## Strategies

The Multi-Provider supports multiple ways of deciding how to evaluate the set of providers it is managing, and how to deal with any errors that are thrown.

Strategies must be adaptable to the various requirements that might be faced in a multi-provider situation.
In some cases, the strategy may want to ignore errors from individual providers as long as one of them successfully responds.
In other cases, it may want to evaluate providers in order and skip the rest if a successful result is obtained.
In still other scenarios, it may be required to always call every provider and decide what to do with the set of results.

The strategy to use is passed in to the Multi-Provider.

By default, the Multi-Provider uses the “FirstMatchStrategy”.

Here are some standard strategies that come with the Multi-Provider:

### First Match

Return the first result returned by a provider.
Skip providers that indicate they had no value due to `FLAG_NOT_FOUND`.
In all other cases, use the value returned by the provider.
If any provider returns an error result other than `FLAG_NOT_FOUND`, the whole evaluation should error and “bubble up” the individual provider’s error in the result.

As soon as a value is returned by a provider, the rest of the operation should short-circuit and not call the rest of the providers.

### First Successful

Similar to “First Match”, except that errors from evaluated providers do not halt execution.
Instead, it will return the first successful result from a provider. If no provider successfully responds, it will throw an error result.

### User Defined

Rather than making assumptions about when to use a provider’s result and when not to (which may not hold across all providers) there is also a way for the user to define their own strategy that determines whether to use a result or fall through to the next one.

## Installation

<!-- x-release-please-start-version -->

```xml

<dependency>
    <groupId>dev.openfeature.contrib.providers</groupId>
    <artifactId>multi-provider</artifactId>
    <version>0.0.2</version>
</dependency>
```

<!-- x-release-please-end-version -->

## Usage

Usage example:

```
...
List<FeatureProvider> providers = new ArrayList<>(2);
providers.add(provider1);
providers.add(provider2);

// initialize using default strategy (first match)
MultiProvider multiProvider = new MultiProvider(providers);
OpenFeatureAPI.getInstance().setProviderAndWait(multiProvider);

// initialize using a different strategy
multiProvider = new MultiProvider(providers, new FirstSuccessfulStrategy());
...
```

See [MultiProviderTest](./src/test/java/dev/openfeature/contrib/providers/multiprovider/MultiProviderTest.java)
for more information.

