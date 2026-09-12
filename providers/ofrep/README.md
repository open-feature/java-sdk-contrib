# OFREP Provider for OpenFeature

This provider allows to connect to any feature flag management system that supports OFREP.

## Installation
For Maven
<!-- x-release-please-start-version -->
```xml
<dependency>
  <groupId>dev.openfeature.contrib.providers</groupId>
  <artifactId>ofrep</artifactId>
  <version>0.0.2</version>
</dependency>
```

For Gradle
```groovy
implementation 'dev.openfeature.contrib.providers:ofrep:0.0.2'
```
<!-- x-release-please-end-version -->

## Configuration and Usage

### Usage
```java
OfrepProviderOptions options = OfrepProviderOptions.builder().build();
OfrepProvider ofrepProvider = OfrepProvider.constructProvider(options);
```
### Example
```java
import dev.openfeature.contrib.providers.ofrep.OfrepProvider;
import dev.openfeature.contrib.providers.ofrep.OfrepProviderOptions;
import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.MutableContext;
import dev.openfeature.sdk.OpenFeatureAPI;

public class App {
    public static void main(String[] args) {
        OpenFeatureAPI openFeatureAPI = OpenFeatureAPI.getInstance();

        OfrepProviderOptions options = OfrepProviderOptions.builder().build();
        OfrepProvider ofrepProvider = OfrepProvider.constructProvider(options);

        openFeatureAPI.setProvider(ofrepProvider);

        Client client = openFeatureAPI.getClient();

        MutableContext context = new MutableContext();
        context.setTargetingKey("my-identify-id");

        FlagEvaluationDetails<Boolean> details = client.getBooleanDetails("my-boolean-flag", false, context);
        System.out.println("Flag value: " + details.getValue());

        openFeatureAPI.shutdown();
    }
}
```

### Configuration options

Options are passed via `OfrepProviderOptions`, using which default values can be overridden.

Given below are the supported configurations:


| Option name | Type    | Default   | Description
| ----------- | ------- | --------- | ---------
| baseUrl      | String  | http://localhost:8016 | Override the default OFREP API URL.
| headers      | ImmutableMap  | Empty Map | Add custom headers which will be sent with each network request to the OFREP API.
| timeout      | Duration  | 10 Seconds | The timeout duration to establishing the connection.
| proxySelector      | ProxySelector  | ProxySelector.getDefault() | The proxy selector used by HTTP Client.
| executor      | Executor  | Thread Pool of size 5 | The executor used by HTTP Client.


## Provider conformance (TCK)

This provider adopts the [OpenFeature Provider TCK](../../tools/tck/README.md) as a single suite,
`OfrepTckTest`. Read that class before changing it: it records which capabilities are declared,
which are withheld and why — several are withheld because OFREP puts the decision on the server
rather than in the provider, which is a fact about the protocol and not a defect — and every known
deviation.

Because OFREP is a protocol rather than a vendor, the backend under test is simply something that
speaks it. The suite reuses the unmodified `flagd-testbed` image and its launchpad control API.

**The suite is Docker-gated and excluded from the default build**, via
`<testExclusions>**/e2e/*.java</testExclusions>` in this module's POM. That property is the
repository's convention for a Docker-dependent suite, fed to Surefire by the parent POM; the parent
defines no default, so each module that wants the gate declares it. This module did not, which meant
`mvn verify` started a Compose stack and the suite ran — and failed — in every job that touched
`providers/ofrep`. That is the second of the two mistakes
[Appendix F: Running the suite in CI](https://github.com/open-feature/spec/blob/main/specification/appendix-f-provider-conformance.md#running-the-suite-in-ci)
names, and the appendix has the reasoning for the whole policy; the exclusion is the fix and this
paragraph is the other half of it.

This module has no profile that touches the property, so both spellings resolve the same way —
checked rather than read, because that is the appendix's other warning:

```bash
mvn -Pe2e -pl providers/ofrep help:evaluate -Dexpression=testExclusions -DforceStdout
```

The exclusion is Surefire's and not the compiler's, so the suite still builds against the harness in
every job.

The consequence is that **no CI job runs the suite**, so a maintainer runs it by hand before merging
a change to the provider's resolution or error behaviour, and quotes the result in the pull request.
A scheduled or path-filtered workflow was considered and declined: a suite whose red is diagnosed by
whoever happens to read the notification is worse than one whose red is diagnosed by the person who
caused it.

```bash
mvn -pl providers/ofrep -am -DtestExclusions= -Dtest=OfrepTckTest \
    -Dsurefire.failIfNoSpecifiedTests=false test
```

The suite is currently **expected to fail** on the failures enumerated in `OfrepTckTest`, which come
from flags the pinned testbed image does not serve (open-feature/flagd-testbed#392). Anything else
is a regression.
