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
`OfrepTest`, in a source directory of its own: `src/test/java/.../ofrep/tck/`. It is the module's
only Docker-dependent test package, and having it be a directory rather than a filename convention
is what lets every selector below name a place instead of a pattern. In a package called `tck` the
class needs no further label; the fully-qualified name still carries everything.

Read that class before changing it: it records which capabilities are declared,
which are withheld and why — several are withheld because OFREP puts the decision on the server
rather than in the provider, which is a fact about the protocol and not a defect — and every known
deviation.

Because OFREP is a protocol rather than a vendor, the backend under test is simply something that
speaks it. The suite reuses the unmodified `flagd-testbed` image and its launchpad control API.

**The suite is Docker-gated and excluded from the default build**, via
`<testExclusions>**/tck/*.java</testExclusions>` in this module's POM. That property is the
repository's convention for a Docker-dependent suite, fed to Surefire by the parent POM; the parent
defines no default, so each module that wants the gate declares it. This module did not, which meant
`mvn verify` started a Compose stack and the suite ran — and failed — in every job that touched
`providers/ofrep`. That is the second of the two mistakes
[Appendix F: Running the suite in CI](https://github.com/open-feature/spec/blob/main/specification/appendix-f-provider-conformance.md#running-the-suite-in-ci)
names, and the appendix has the reasoning for the whole policy; the exclusion is the fix and this
paragraph is the other half of it.

Exactly one profile touches the property — `tck`, below — and `e2e`, which the repository's CI
activates on every push, does not exist in this module at all. Checked rather than read, because
that is the appendix's other warning:

```bash
mvn -Pe2e -pl providers/ofrep help:evaluate -Dexpression=testExclusions -DforceStdout
```

The exclusion is Surefire's and not the compiler's, so the suite still builds against the harness in
every job.

**The conformance suite has a profile of its own, `tck`.** That is the separation the appendix asks
for, and the reason is what a red build *says* rather than how long it takes: a conformance run
carries failures by design, wherever `OfrepTest` declares a `knownDeviation`, so a signal shared
with a suite that is expected green ends with somebody silencing the informative half. The profile
drops the `tck` directory from the exclusion and narrows Surefire's includes to `**/tck/*.java` in
the same breath, so it runs the conformance suite and nothing else — not the module's unit tests.
Both halves are needed: the include alone leaves the exclusion in force and runs nothing, and
dropping the exclusion alone runs the unit tests too. Nothing activates it in CI.

The consequence is that **no CI job runs the suite**, so a maintainer runs it by hand before merging
a change to the provider's resolution or error behaviour, and quotes the result in the pull request.
A scheduled or path-filtered workflow was considered and declined: a suite whose red is diagnosed by
whoever happens to read the notification is worse than one whose red is diagnosed by the person who
caused it.

```bash
# once, if tools/tck is not in your local repository yet
mvn -pl tools/tck -am -DskipTests install

mvn -Ptck -pl providers/ofrep test
```

**Do not add `-am` to the run itself**: it pulls `tools/tck` into the reactor and runs its 246 tests
before the first scenario, so a failure there comes out as a `-Ptck` failure — the signal-mixing this
step exists to prevent, reintroduced by a flag. The separate `install` is what `-am` was there for.

The suite is currently **expected to fail** on the failures enumerated in `OfrepTest`, which come
from flags the pinned testbed image does not serve (open-feature/flagd-testbed#392). A clean run is
65 scenarios, 46 passing, 17 skipped and 2 failing.

It is also **intermittently flaky**, and `OfrepTest`'s class javadoc says why: about half the runs
carry one or two extra failures where an evaluation comes back as the code default or as
`FLAG_NOT_FOUND`, on a scenario that moves from run to run. That is the testbed readiness window of
open-feature/flagd-testbed#394, not a provider defect and not something to cover with a sleep. Repeat
the run before treating an extra failure as a regression; anything that reproduces is one.
