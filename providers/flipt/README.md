# Official Flipt OpenFeature Provider for Java

[Flipt](https://www.flipt.io/) OpenFeature Provider can provide usage for Flipt via OpenFeature Java SDK.

## Installation

<!-- x-release-please-start-version -->

```xml
<dependency>
    <groupId>dev.openfeature.contrib.providers</groupId>
    <artifactId>flipt</artifactId>
    <version>0.1.4</version>
</dependency>
```

<!-- x-release-please-end-version -->

## Concepts

* Boolean evaluation gets feature boolean evaluation / enabled status.
* Object evaluation gets the variant attachment, parsed from its JSON representation.
* Integer and float evaluations get the feature variant key, with lossless numeric coercion.
* Other evaluations get the feature variant key.

## Usage

Flipt OpenFeature Provider uses Flipt's [Server Java SDK](https://github.com/flipt-io/flipt-server-sdks/tree/main/flipt-java).

### Usage Example

```java
// create a Flipt client and provider
FliptClientBuilder fliptClientBuilder = FliptClient.builder().url(apiUrl);
FliptProviderConfig fliptProviderConfig = FliptProviderConfig.builder()
        .fliptClientBuilder(fliptClientBuilder)
        .build();

// create OpenFeature provider
FeatureProvider fliptProvider = new FliptProvider(fliptProviderConfig);
OpenFeatureAPI.getInstance().setProviderAndWait("sync", fliptProvider);
client = OpenFeatureAPI.getInstance().getClient("sync");

MutableContext evaluationContext = new MutableContext();
evaluationContext.setTargetingKey(FLAG_NAME + "_targeting_key");
featureEnabled = client.getBooleanValue(FLAG_NAME, false, evaluationContext);
variant = client.getStringValue(VARIANT_FLAG_NAME, "", evaluationContext);
```

See [FliptProviderTest.java](./src/test/java/dev/openfeature/contrib/providers/flipt/FliptProviderTest.java) for more information.

### Additional Usage Details

* Additional evaluation data can be received via flag metadata, such as:
  * *variant-attachment* - string
* A disabled flag resolves to the caller's default value with reason `DISABLED`.
* A flag with no default variant resolves to the caller's default value with reason `DEFAULT`.
* A missing flag reports `FLAG_NOT_FOUND`; a flag requested as the wrong type reports
  `TYPE_MISMATCH`; neither throws out of the evaluation.

## Provider conformance suite

The provider runs the OpenFeature Provider TCK against a throwaway Flipt backend:

```sh
mvn -Ptck -pl providers/flipt -am test
```

The backend is the shared testbed published from
[flipt-io/openfeature-tck](https://github.com/flipt-io/openfeature-tck). It is Flipt plus a control
server that starts, stops and reseeds the bundled Flipt process, so the suite can simulate outages
and configuration changes without restarting the container. The adoption pins
`ghcr.io/flipt-io/openfeature-tck:0.1.0`; set `FLIPT_TESTBED_IMAGE` to a locally built tag when
changing the testbed.

The suite declares `OBJECT`, `TARGETING`, `DISABLED_FLAGS` and `NUMERIC_COERCION`. It
withholds lifecycle, events, reinitialization, stale and configuration-change because the
provider has no observable lifecycle or event support, variants because Flipt booleans carry
no variant, string typing because Flipt stores scalar values as variant-key text, and
standard reasons because Flipt reports the same `DEFAULT_EVALUATION_REASON` for a rule-less
flag and a targeting rule that matched nothing. The Flipt-specific reason behavior is
covered by `flipt-reason.feature`, which the build downloads from the tagged testbed
repository and unpacks into the test classpath. This repository keeps no copy of it.

## Flipt Provider Tests Strategies

Unit test based on WireMock for API mocking.  
