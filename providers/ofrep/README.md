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
`OfrepTest`, in `src/test/java/.../ofrep/tck/`. **Read that class before changing it.** It records
which capabilities are declared, which are withheld and why — several are withheld because OFREP puts
the decision on the server rather than in the provider, which is a fact about the protocol and not a
defect — and every known deviation. Because OFREP is a protocol rather than a vendor, the backend
under test is simply something that speaks it: the suite reuses the unmodified `flagd-testbed` image
and its launchpad control API.

```bash
# once, if tools/tck is not in your local repository yet
mvn -pl tools/tck -am -DskipTests install

mvn -Ptck -pl providers/ofrep test
```

Do not add `-am` to the run itself; the TCK README says why.

The suite is Docker-gated and excluded from the default build by
`<testExclusions>**/tck/*.java</testExclusions>` in this module's POM, and the `tck` profile above is
the only thing that undoes it — this module has no `e2e` profile, so the `-Pe2e` that CI activates on
every push changes nothing here. The exclusion was missing until recently, which meant `mvn verify`
started a Compose stack and ran the suite, red, in every job that touched `providers/ofrep`.

**No CI job runs it**, so a maintainer runs it by hand before merging a change to the provider's
resolution or error behaviour, and quotes the result in the pull request.

A clean run is **65 scenarios: 46 passing, 17 skipped, 2 failing**, the two failures being flags the
pinned testbed image does not serve (open-feature/flagd-testbed#392). It is also **intermittently
flaky** — about half the runs carry one or two extra failures where an evaluation comes back as the
code default or as `FLAG_NOT_FOUND`, on a scenario that moves from run to run. That is the testbed
readiness window of open-feature/flagd-testbed#394, not a provider defect and not something to cover
with a sleep; `OfrepTest`'s javadoc has the detail. Repeat the run before treating an extra failure as
a regression — anything that reproduces is one.
