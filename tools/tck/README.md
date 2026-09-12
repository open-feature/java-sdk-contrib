# OpenFeature Provider TCK (Java)

A conformance suite any OpenFeature Java provider can adopt to verify that it implements the
provider contract of the [specification](https://openfeature.dev/specification/).

It is the Java implementation of [Appendix F][appendix-f], which defines the scenarios, the canonical
flag set, the control API and the capability vocabulary that every language's TCK shares. **Read it
for anything true of the suite rather than of this artifact** — what is tested and what is not, the
rules for declaring a capability, what a known deviation means, and how to run an adoption in CI.
Tracking issue: [open-feature/spec#417][tracking].

> **Status: proof of concept.** The scenario set covers each architectural mechanism once rather than
> exhaustively. Expect breaking changes.

## Installation

<!-- x-release-please-start-version -->
```xml
<dependency>
  <groupId>dev.openfeature.contrib.tools</groupId>
  <artifactId>tck</artifactId>
  <version>0.1.0</version>
  <scope>test</scope>
</dependency>
```
<!-- x-release-please-end-version -->

Java 11+ and JUnit 5. Docker is needed only by providers with an external backend.

**Testcontainers is not transitive.** `ContainerizedProviderTckTest` owns a `ComposeContainer`, so this
artifact compiles against Testcontainers but declares it `provided` and `optional`. A containerised
adopter adds `org.testcontainers:testcontainers` itself — one line for the adopters that need it, and
it keeps Testcontainers off the classpath of every backend-less adopter, which would otherwise resolve
it for a class it never loads.

**The SDK is a `provided` version range**, `[1.21.0,1.99999)`, inherited from this repository's parent
POM — never a pin. A conformance suite that forces an SDK upgrade before you can run it is one nobody
runs; the TCK uses only long-stable API.

## Quick start

Two base classes, and one question chooses between them: **does your provider talk to something
outside the JVM?**

| | Extend | Backend control |
| --- | --- | --- |
| External backend | `ContainerizedProviderTckTest` | `HttpBackendControl`, over the HTTP control API |
| No backend — in-memory, environment variables, a local file | `ProviderTckTest` | an in-process `BackendControl` |

### A provider with a backend

Write a Compose file at `src/test/resources/tck/docker-compose.yaml` exposing your backend and its
[control API][control-api], then one test class:

```java
public class MyProviderTest extends ContainerizedProviderTckTest {

    @Override
    public File composeFile() {
        return new File("src/test/resources/tck/docker-compose.yaml");
    }

    @Override
    public List<Integer> backendPorts() {
        return Collections.singletonList(5000);
    }

    @Override
    public FeatureProvider createProvider(BackendEndpoint endpoint) {
        return new MyProvider(endpoint.host(), endpoint.port(5000));
    }

    @Override
    public FeatureProvider createUnavailableProvider() {
        return new MyProvider("localhost", 9999);
    }
}
```

That is the whole adoption — one file, no registration. The class is simultaneously the JUnit suite
and the harness; the Compose lifecycle, port discovery, control API calls, provider registration,
event awaiting and teardown belong to the TCK, and the three artifacts are packaged in the JAR, so an
adoption needs no submodule. **If you find yourself adding test infrastructure to this class, that is
a defect here — please open an issue.**

`createUnavailableProvider()` should point at a closed port on localhost, not at your stack: the stack
stays up and outages are simulated through the control API. Give it a short connection deadline,
because the failure scenarios assert that failure is reported *promptly*.

**Seed your backend with the [canonical flag set][flags]** — several of its properties are
load-bearing and easy to break while seeding, so read the [assets README][assets] rather than retyping
the file.

### A provider with no backend

`InProcessBackendControl` implements the in-process path for the SDK's `InMemoryProvider`, seeded from
the packaged flag set. The adoption is three methods:

```java
public class MyProviderTest extends ProviderTckTest {

    private final InProcessBackendControl control = new InProcessBackendControl();

    @Override
    public BackendControl backendControl() {
        return control;
    }

    @Override
    public FeatureProvider createProvider() {
        return control.createProvider();
    }

    @Override
    public Set<Capability> capabilities() {
        return EnumSet.of(Capability.EVENTS, Capability.CONFIGURATION_CHANGE, Capability.OBJECT);
    }
}
```

One object backs both methods because in-process the flag store and the provider are the same thing:
`changeFlag()` has to reach the live provider instance to emit an event from it.

This path is a narrow allowance for providers with **nothing to contract with**, not an invitation to
skip the control API — see [Appendix F, "Providers with no backend"][appendix-f]. It leaves
`disconnect()` and `reconnect()` throwing, and withholding `STALE` and `UNAVAILABLE_INIT` is what
keeps that honest: declare one anyway and the scenario fails with an `UnsupportedOperationException`
naming the fix, because reaching an unsupported operation from a scenario that actually ran is a
test-configuration bug, never a skip.

### Several provider modes

A provider with more than one transport writes **one class per mode and nothing else** — no
registration, no system property, no build configuration. Each class is its own suite with its own
Compose stack, and abstract classes are not run, so an intermediate base is safe. Per-mode differences
may include timing as well as wiring: flagd's in-process resolver syncs the whole ruleset before
reporting ready, so it needs a longer initialisation deadline than its RPC mode.

## The options

Eight Compose concepts, with the same names and defaults in every language's TCK, spelled here as
overridable methods on `ContainerizedProviderTckTest`.

| Concept | Required | Default | Java |
| --- | --- | --- | --- |
| Compose file | yes | — | `File composeFile()` — resolved relative to the Maven module directory |
| Backend service | no | `backend` | `String backendService()` — the service hosting both the control API and the backend |
| Backend ports | yes | — | `List<Integer> backendPorts()` — container-internal ports the *provider* connects to. Do not list the control port; it is exposed automatically |
| Control port | no | `8080` | `int controlPort()` |
| Additional ports | no | none | `Map<String, List<Integer>> additionalPorts()` — extra service → ports, resolved through the endpoint by service name |
| Backend configuration | no | `default` | `String backendConfiguration()` — the name passed to `POST /start` |
| Startup timeout | no | 60s | `Duration startupTimeout()` — the stack and its control API becoming reachable |
| Endpoint | — | — | `BackendEndpoint` — `host()` and `port(internalPort)`, optionally qualified by service |

**Never pin host ports.** They are mapped dynamically and discovered after startup, which is why the
provider comes from a factory rather than a constant. The stack may hold any number of extra
containers; the TCK only cares about the conventions above.

`backendConfiguration()` names a configuration the **backend** understands; `configuration()` names the
mode of the **provider**. The bare word `configuration` meant opposite things in three of the four
languages' first drafts, which is why these two are spelled apart.

### Timeouts

| Method | Default | What it bounds |
| --- | --- | --- |
| `eventTimeout()` | 12s | waiting for a provider event |
| `readyTimeout()` | 30s | waiting for a provider to reach a lifecycle state |
| `startupTimeout()` | 60s | the Compose stack and its control API becoming reachable |

Set `eventTimeout()` to comfortably exceed your worst-case detection latency, or the suite reports
timeouts that are really impatience; scenarios asserting promptness as part of their point use the
explicit `within {int}ms` step, which always wins. Every entry is a **bound on an await** and none is
a pause — nothing sleeps after a control call, for the reason Appendix F's control-API invariants give.

### Identifying the run

`configuration()` names which of the provider's *modes* this suite exercised — flagd's RPC and
in-process resolvers run two suites whose results are not interchangeable. It defaults to the suite
class name, hyphenated with the JUnit suffix dropped. **Check it if your suite lives in a package that
already names the provider**, which is what the layout below recommends: `InProcessTest` in
`...providers/flagd/tck/` derives `in-process`, which says nothing about whose in-process mode it was
to a reader away from this repository. flagd's two suites state `flagd-rpc` and `flagd-in-process`
outright.

`BackendControl.controlApi()` says which contract a run was conducted under, `ControlApi.HTTP` or
`ControlApi.IN_PROCESS`. It is abstract and the enum closed, because Appendix F requires the control
to *state* the path rather than have the harness infer it from a concrete type.

## Declaring capabilities

Each scenario exercising an optional part of the contract carries a tag, and a provider declares what
it supports. Undeclared ones are reported as **skipped with the reason** — never as passed. Each name
below is a member of `Capability`.

| Capability | Tag | | Capability | Tag |
| --- | --- | --- | --- | --- |
| `LIFECYCLE` | `@lifecycle` | | `UNAVAILABLE_INIT` | `@unavailable` |
| `REINITIALIZATION` | `@reinitialization` | | `NUMERIC_COERCION` | `@numeric-coercion` |
| `EVENTS` | `@events` | | `TARGETING` | `@targeting` |
| `STALE` | `@stale` | | `STANDARD_REASONS` | `@standard-reasons` |
| `CONFIGURATION_CHANGE` | `@configuration-change` | | `STRING_TYPING` | `@string-typing` |
| `OBJECT` | `@object` | | `LARGE_INTEGERS` | `@large-integers` ¹ |
| `VARIANTS` | `@variants` | | `CACHING` | `@caching` ² |
| `DISABLED_FLAGS` | `@disabled-flags` | | | |

¹ not declarable in Java &nbsp;&nbsp; ² reserved, not declarable

What each tag means, and — more importantly — **when to declare one and when to withhold it** are
[Appendix F's][appendix-f], under "Capabilities" and "Rules for declaring". The decision is per
*scenario* rather than per tag, and three of the four implementations got that wrong in three
different directions, so it is worth the read.

The default is every *declarable* capability. **Narrow it, do not widen it**: start from the default,
run the suite, and remove only what your provider genuinely cannot do.

```java
@Override
public Set<Capability> capabilities() {
    return Capability.declarableExcept(Capability.STALE);
}
```

**Use `Capability.declarable()` and `declarableExcept(...)`, not `EnumSet.complementOf(...)`.**
`complementOf(EnumSet.of(X))` reads as "everything except X" and in fact means "every other enum
constant", reserved and inexpressible ones included. The flagd suite said exactly that and published
`"declared": [..., "@targeting", "@caching"]` for two capabilities nobody had claimed. The two
factories mean what the first one looks like, and naming a refused capability directly is an error
rather than a quiet correction, so you cannot get this wrong silently either.

**`LARGE_INTEGERS` is inexpressible in Java, and you do not have to know anything about it.**
`Client.getIntegerDetails` takes and returns a 32-bit `Integer`, which has no room for 2^53 − 1, so no
Java provider can be asked the question until the SDK grows a wider accessor. Appendix F's rule is
that such a capability is refused by the implementation rather than left to every adopter to remember
— this module had the same paragraph restated in four places before it was. Its scenario is skipped
with a reason naming the **SDK**, so a report's reader can tell *"this provider declined"* from *"no
Java provider can be asked"*; the 32-bit precision scenario is untagged and always runs. A reserved
capability is a different thing and its reason says so. Neither refusal is a defect, and neither needs
a `KnownDeviation`.

### Known deviations

**A `knownDeviations` entry says: this provider fails to do something it is required to do.** The
requirement must be a numbered `MUST`, or a rule the implementation bound itself to elsewhere. Where
the specification *permits* the choice, withholding the capability **is** the honest report.

```java
@Override
public List<KnownDeviation> knownDeviations() {
    return List.of(KnownDeviation.tracked(
            Capability.NUMERIC_COERCION,
            "https://github.com/open-feature/java-sdk-contrib/issues/1234",
            "float-flag through the integer API returns 0 with no error code"));
}
```

The two legitimate shapes, and why the declared-and-failing one is preferred, are
[Appendix F's][appendix-f]. `summary` is required and `issue` is not — `KnownDeviation.untracked(...)`
is the untracked form. The capability may be `null` for a mandatory, ungated scenario; it may not be
`CACHING` or `LARGE_INTEGERS`, since no scenario was ever put to your provider for either, and both
are refused where you write them. Empty is the default, and it is silence rather than a claim of
having none.

## Running it

```bash
# once, if this module is not in your local repository yet
mvn -pl tools/tck -am -DskipTests install

mvn -Ptck -pl providers/<your-provider> test
```

**Resist adding `-am` to the second line.** It pulls this module — and anything else the adoption
depends on — into the reactor and runs their suites before the first scenario, so a failure in any of
them comes out as a `-Ptck` failure. The one-off `install` is what `-am` was there for.

Scenarios run **serially**, enforced over any `cucumber.execution.parallel.enabled=true` in your
module: control API state is global to the stack, so concurrent scenarios corrupt each other and the
symptom looks like a flaky provider. The stack starts once per suite and is never restarted.

**Put the adoption in a `tck` package of its own**, beside your module's other test packages rather
than inside one — in `providers/flagd` that is
`src/test/java/dev/openfeature/contrib/providers/flagd/tck/`, a sibling of `e2e` rather than a corner
of it. Why a conformance suite is not a kind of end-to-end test, and why selection by directory beats
selection by filename, are [Appendix F's][appendix-f] under "Running the suite in CI". Once the
directory selects, names that repeat it say the same thing twice — `FlagdRpcTckTest` in package
`...flagd.tck` is `RpcTest` — but **keep the `*Test` suffix**, which Surefire's default includes need,
and check `configuration()` when you rename.

### The Maven mechanism

Appendix F has the reasoning for excluding an adoption from the default build and giving it a step of
its own; this is only how that is spelled here. The exclusion is the `testExclusions` property, which
the parent POM feeds to Surefire and gives no default, so a module that wants the gate declares it,
listing every Docker-dependent package — `providers/flagd` has its legacy `e2e` suites too:

```xml
<properties>
  <testExclusions>**/e2e/*.java,**/tck/*.java</testExclusions>
</properties>
```

It is a **Surefire** exclusion and not a compiler one, so the adoption still compiles against the
harness in every build — the "keep it typechecked by something that runs ordinarily" property the
appendix asks for, which Maven gives for free.

**Then resolve the property under every profile your CI activates, rather than reading the POM.** Both
halves of the appendix's warning happened in this repository — one adoption never declared the
property, the other had a profile putting it back — and both were found by running this:

```bash
mvn -Pe2e -pl providers/<your-provider> help:evaluate -Dexpression=testExclusions -DforceStdout
```

The dedicated step is a profile, one per adopting module, named `tck` because JavaScript's `nx tck`
target and Python's `poe test-tck` task already spell it that way. It drops the `tck` directory from
the exclusion **and** narrows Surefire's includes to it:

```xml
<profile>
  <id>tck</id>
  <properties>
    <!-- what is left of the exclusion; the include below does the selecting -->
    <testExclusions>**/e2e/*.java</testExclusions>
  </properties>
  <build><plugins><plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration><includes><include>**/tck/*.java</include></includes></configuration>
  </plugin></plugins></build>
</profile>
```

**Both halves are needed.** Dropping alone runs the module's unit tests alongside the suites;
narrowing alone leaves the exclusion in force and runs nothing.

## The canonical set cannot be reduced

Extending the suite is safe by convention. Shrinking it is what a conformance suite has to prevent,
because a run that asks fifty-four of the fifty-six questions and reports success is
indistinguishable, in every artifact it produces, from one that asked all fifty-six.

`CanonicalScenarioGuard` is an ordinary JUnit test that the suite selects, and it fails the build if
this run is set up to execute less than the canonical set:

- a feature file added to `gherkin/`, or shadowing a canonical one — the selected scenarios no
  longer match what this artifact ships, which it reads from its own JAR rather than through the
  classpath
- `cucumber.filter.tags` or `cucumber.filter.name` — Cucumber applies these by skipping scenarios at
  execution, so the run is filtered however the plan looks
- selectors or glue overridden in your `junit-platform.properties`

It checks the setup rather than counting afterwards: both the discovered plan and the run's filter
configuration are settled before the first scenario, so the check needs no backend and takes no
measurable time. Where its result appears in the run depends on the order the JUnit Platform executes
the suite's two engines in, which is not specified. Extension scenarios are ignored: the check is
defined over `gherkin/` alone.

Narrowing a run legitimately is what `capabilities()` is for — those scenarios are reported as
skipped with a reason, which a filtered scenario is not. To filter anyway while debugging, set
`-Dprovider.tck.partial=true` (or `PROVIDER_TCK_PARTIAL`). The guard then reports itself as
**skipped** rather than passed, so the run states that its canonical set was not verified.

What the guard does not establish is that the canonical files contain what they should — a
replacement placing its scenarios on the same lines would satisfy it. That is covered better
elsewhere: the results stream carries the `source` of every feature that executed, and
`tck.specRevision` says which revision it should match.

## Conformance reports

Set `PROVIDER_TCK_REPORT_DIR` and each suite writes two files: an envelope conforming to the
[report schema][report-schema] in the specification, and the run's results as a
[Cucumber Messages][messages] stream.

```console
$ PROVIDER_TCK_REPORT_DIR=./reports mvn test -Dtest='Flagd*TckTest'
$ ls reports/
flagd-in-process.json  flagd-in-process.ndjson  flagd-rpc.json  flagd-rpc.ndjson
```

`-Dprovider.tck.report.dir=...` does the same thing and is often easier to pass through Maven. The
environment variable is the portable spelling — every language's TCK reads it, so one cross-language
CI job can set one thing.

It is an environment variable rather than a method on `ProviderTckHarness` so that emitting a report
is a property of the run and not of the code: CI sets it, a developer running the suite locally does
not, and no adopter changes a line to publish one. Unset means no report, which is not an error.
Several suites in one JVM each write their own pair, so flagd's two resolvers do not collide.

### The results are not a format this project defines

The `.ndjson` is a Cucumber Messages stream, produced by Cucumber's own `MessageFormatter` — the
same class the built-in `message:<path>` plugin instantiates, so the bytes are what
`--plugin message:...` would have written. It already carries everything a per-scenario report would
have had to invent: the outcome of every scenario, its tags including any set on an individual
`Examples` block, an exact Scenario Outline row identity, and the source of every feature that ran.

The plugin exists rather than the built-in one because a `@ConfigurationParameter` value is a
compile-time constant, so the built-in plugin's path cannot be derived from the directory the run
asked for — and flagd's two suites would write to the same file.

Reading it needs no special tooling, but it does need one thing understood: **a scenario's outcome
is the most severe result among its steps**, hooks included. `testCaseFinished` carries no status of
its own. That is what makes a capability-gated skip truthful, because the aborted `@Before` hook
contributes a `SKIPPED` result that outranks every step it stopped from running.

```console
$ jq -c 'select(.testStepFinished) | .testStepFinished
         | {c: .testCaseStartedId, s: .testStepResult.status}' reports/flagd-rpc.ndjson \
    | jq -s 'group_by(.c) | map({s: (map(.s) | if any(. == "FAILED") then "FAILED"
                                              elif any(. == "SKIPPED") then "SKIPPED"
                                              else "PASSED" end)})
             | group_by(.s) | map({(.[0].s): length}) | add'
{
  "PASSED": 28,
  "SKIPPED": 1
}
```

The [`cucumber-query`](https://github.com/cucumber/messages/tree/main/java) helpers do this properly
and in several languages; the above is only to show that the fact is in the file.

### What identifies a scenario

`pickle.astNodeIds`. For a scenario compiled from a Scenario Outline it is
`[scenario id, table row id]`, and the second entry resolves in the `gherkinDocument` message to the
`Examples` row the scenario was built from. Feature and name are not enough — the type-mismatch
matrix in `errors.feature` is eleven rows sharing one name — and this is exact rather than derived:

```console
$ jq -c 'select(.pickle) | .pickle
         | select(.name == "Requesting the wrong type returns the code default")
         | {id, row: .astNodeIds[1]}' reports/flagd-rpc.ndjson | head -3
{"id":"6c8debd2-...","row":"ab8b4a4b-..."}
{"id":"a7c76b0a-...","row":"63d6d6c8-..."}
{"id":"fecd333d-...","row":"bbd7f5ee-..."}
```

An earlier version of this module reverse-engineered the same fact by re-parsing the feature source
and matching a pickle's reported line number against the Examples tables. The stream states it
outright, which is the whole argument for a standard format over one we maintain.

### What the envelope is for

A Messages stream cannot say what it was a test *of*. The envelope carries the four things no
standard results format identifies:

- **`provider`** — what the provider calls itself through its own metadata, not the suite name. The
  suite name is chosen to read well in a failure message (`flagd-rpc`), which makes it the
  *configuration*, and it is reported as such. One provider with two materially different modes
  produces two reports that are not interchangeable. Derived from the suite class name
  (`FlagdInProcessTckTest` → `flagd-in-process`); override `ProviderTckHarness.configuration()`.
- **`sdk`** — read from the classpath rather than declared, because the TCK depends on an SDK version
  *range* so that adopting it can never force an upgrade. What a consumer actually ran against is
  only knowable at runtime.
- **`tck`** — which implementation asked the questions, and `specRevision`, the open-feature/spec
  commit the packaged artifacts came from. Baked into the JAR at build time from this module's POM:
  the artifacts travel in the JAR, the repository they came from does not. The executed Gherkin no
  longer rests on that pin alone — the stream carries the `source` of every feature, so it can be
  diffed against the revision — but the pin is what identifies the two artifacts the stream does not
  carry, `flags/canonical-flags.json` and `openapi/control-api.yaml`.
- **`declaration`** — the capability set the provider claims. This is an **input** to reading the
  results, not a summary of them, which is why it cannot be derived from the stream. The stream says
  a scenario was skipped; only the declaration says whether that is because the provider declines the
  capability it needed. Given the declaration and a scenario's tags — both present — the reason for
  each skip follows, so it does not have to be transported per scenario.

`knownDeviations` is the one thing neither the stream nor the declaration can express: whether a
withheld capability is a limitation or a bug. See
[Saying that a withheld capability is a defect](#saying-that-a-withheld-capability-is-a-defect).

`results.digest` covers the `.ndjson`, so a consumer that fetched the two separately can tell that
what it has is what the envelope describes.

### What the report is for

This suite promises that a scenario skipped for an undeclared capability is reported as skipped with
the reason and *never* as passed — and a promise is not a check. The stream records every scenario
individually, so a consumer can verify the rule instead of trusting a runner's headline number. Go's
runner counts capability-gated skips in its **passed** tally, which is exactly the failure mode this
makes impossible to hide.

Every scenario appears exactly once, whatever happened to it. A report that quietly omitted the
scenarios it did not run would satisfy every rule above and still mislead, because a reader would
have no way to know how many questions went unasked. `ConformanceReportPluginTest` runs a fixture
suite through the real Cucumber engine and asserts both properties over the emitted stream.

[report-schema]: https://github.com/open-feature/spec/blob/main/specification/assets/provider-tck/report/conformance-report.schema.json
[messages]: https://github.com/cucumber/messages

## Extending it

A provider with features of its own — flagd's `fractional` targeting, a vendor's proprietary mode —
extends the suite rather than maintaining a second one. Two files, no annotations:

```
src/test/resources/extensions/fractional.feature
src/test/java/openfeature/tck/extensions/FractionalSteps.java   // package openfeature.tck.extensions
```

Both are already selected by `ProviderTckTest`, so your scenarios run **inside** the suite: same
backend lifecycle, same `@BeforeAll`, same `BackendControl`, and canonical steps are on the glue path
too, so an extension scenario can open with `Given a stable provider`. A step class may take
`TckState` as a constructor argument exactly as the canonical steps do, and reach the backend control
and endpoint through `TckRuntime.get()` — **build a client of your own instead and you resolve against
a provider this suite never registered.** If a scenario is portable across providers, send it to the
TCK rather than keeping it as an extension.

`gherkin/` and `extensions/` are Appendix F's names, and being two distinct directories is what makes
shadowing unreachable here: two classpath roots holding the same directory are scanned additively, but
two holding the same directory *and* the same file name are not — one wins silently, so a
`gherkin/errors.feature` in your test resources would *replace* the canonical file and the suite would
report success having run yours. The `extensions/` directory ships in this JAR holding only a README,
because a classpath resource selector naming a resource on no classpath root is a hard discovery error
rather than an empty selection.

`ProviderTck` names every value the suite's annotations carry — `FEATURES`, `EXTENSIONS`, `GLUE`,
`EXTENSION_GLUE`, `ALL_GLUE` and the rest of the Cucumber configuration — so an adopter who does write
a `@ConfigurationParameter` composes rather than copies, an annotation value having to be a
compile-time constant. Keep `ProviderTck.GLUE` in it; dropping it makes every canonical step undefined.

## Java notes

**Suite discovery** relies on the JUnit Platform auto-registering `TckSuiteListener`, declared in this
JAR's `META-INF/services/org.junit.platform.launcher.TestExecutionListener`, which Surefire, Gradle
and IDEs all do by default. If your launcher disables listener auto-registration, register the harness
explicitly at `src/test/resources/META-INF/services/dev.openfeature.contrib.tools.tck.ProviderTckHarness`
and select between several with `-Dopenfeature.tck.harness=RpcTest`.

**This module's own suites are the reference adoption to copy**, and all three run without Docker in
under a second: `InMemoryProviderTckTest` against the SDK's `InMemoryProvider`,
`ControllableProviderTckTest` against a provider with a real initialisation — the only Docker-free
cover the `@lifecycle` feature has — and `MultiProviderTckTest` against `MultiProvider` wrapping one
`InMemoryProvider`, where any difference from the first is attributable to delegation and nothing
else. That last one has already paid for itself: it cannot declare `CONFIGURATION_CHANGE`, because
`MultiProvider` never subscribes to its children and swallows their events —
[java-sdk#1882](https://github.com/open-feature/java-sdk/issues/1882), reproduced from the outside.
Appendix F's carve-out for a self-test withholding a capability over a defect applies to these and
**not** to an adoption.

**The packaged artifacts are generated.** The Gherkin, flag set and control-API document live in
[open-feature/spec][spec] under `specification/assets/provider-tck/`; the `spec` submodule is updated
at `initialize` and the three directories are copied into `src/main/resources/` at
`generate-resources`. **Do not edit `src/main/resources/gherkin/`, `flags/` or `openapi/`** — they are
git-ignored, and changes belong upstream. Appendix F asks that a stale checkout be unrunnable rather
than merely discouraged, since a rebase moves the gitlink while only `git submodule update` moves the
working tree; three things enforce that here. The checkout runs on every build, skippable only through
the dedicated `-Dtck.spec.checkout.skip=true`; the generated directories are emptied before the copy,
so a file present in the old pin and not the new one cannot survive; and `CanonicalAssetDigestTest`
fails the build by digest over all three directories, which is the only one of the three that catches
a pin whose sole change is *content*.

**The step vocabulary** is inherited from the [flagd test harness](https://github.com/open-feature/test-harness)
wherever it was already provider-neutral, so flagd's feature files ported with a near-zero diff; only
`Given a stable flagd provider` and `Given a unavailable flagd provider` were renamed, to drop the
vendor. The canonical set adds seven steps of its own, and each carries its rationale as javadoc on
the method that binds it, in `steps/ProviderSteps` and `steps/FlagSteps` — read those before writing
an extension step, since several of them exist to keep a scenario vendor-neutral in a way that is not
obvious from the wording.

## Known gaps

[Appendix F][appendix-f] carries the suite's gaps — context passthrough beyond the targeting key,
per-flag control operations, caching, `@stale` without containers, hooks, flag metadata, and the
requirements not yet covered. One is Java's alone: **`TckRuntime` is static**, so TCK suites run one
at a time within a JVM fork. Several suites in one fork is fine — they run sequentially, each with its
own Compose stack — but they cannot run concurrently.

## Contributing

See the repository [CONTRIBUTING.md](../../CONTRIBUTING.md). New scenarios should be portable across
providers: a scenario that can only pass against one vendor's backend semantics belongs in that
provider's own suite, not here.

[appendix-f]: https://github.com/open-feature/spec/blob/main/specification/appendix-f-provider-conformance.md
[assets]: https://github.com/open-feature/spec/blob/main/specification/assets/provider-tck/README.md
[control-api]: https://github.com/open-feature/spec/blob/main/specification/assets/provider-tck/openapi/control-api.yaml
[flags]: https://github.com/open-feature/spec/blob/main/specification/assets/provider-tck/flags/canonical-flags.json
[spec]: https://github.com/open-feature/spec
[tracking]: https://github.com/open-feature/spec/issues/417
