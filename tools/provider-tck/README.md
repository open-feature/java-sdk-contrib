# OpenFeature Provider TCK

A conformance test suite that any OpenFeature provider can adopt to verify it implements the
provider contract of the [OpenFeature specification](https://openfeature.dev/specification/).

OpenFeature's central promise is that swapping providers does not change application behaviour.
Today nothing verifies that — every provider tests differently, so "implements the provider
contract" is an unverified claim. This is the shared suite that makes it checkable.

> **Status: proof of concept.** The scenario set is a representative subset covering each
> architectural mechanism once, not exhaustive coverage. See [Known gaps](#known-gaps).

## Installation

<!-- x-release-please-start-version -->
```xml
<dependency>
  <groupId>dev.openfeature.contrib.tools</groupId>
  <artifactId>provider-tck</artifactId>
  <version>0.0.1</version>
  <scope>test</scope>
</dependency>
```
<!-- x-release-please-end-version -->

Requires Java 11+, JUnit 5, and a working Docker daemon.

### OpenFeature SDK compatibility

The TCK declares `dev.openfeature:sdk` as a **`provided` version range** (`[1.21.0,1.99999)`),
inherited from this repository's parent POM. It never pins an SDK version.

That is deliberate. A conformance suite that forces an SDK upgrade before you can run it is a
conformance suite nobody runs. Your build keeps whatever SDK version it already resolves; the TCK
uses only long-stable API — `OpenFeatureAPI`, `Client`, typed evaluation, `ProviderEvent`,
`ProviderState`.

## What it tests, and what it does not

**In scope — the provider contract:**

- mapping backend responses onto typed resolution details (value, variant, reason, error code)
- keeping the integer and float types distinct
- error handling: type mismatch and unknown flag return the code default, report the right error
  code, and never throw
- lifecycle: reaching `READY`, and settling into `ERROR` against an unreachable backend
- events: `PROVIDER_READY`, `PROVIDER_ERROR`, `PROVIDER_STALE`, `PROVIDER_CONFIGURATION_CHANGED`
- that a signalled configuration change is actually applied on re-evaluation

**Out of scope — not the provider's contract:**

- backend evaluation logic, targeting and bucketing correctness. Every flag in the canonical set
  resolves to its default variant with no targeting, so what is under test is the provider's
  mapping of a response, not the backend's decision.
- the provider↔backend wire protocol. How you talk to your backend is your business.
- SDK behaviour. That belongs to the SDK's own test suite.

## Adopting it

Four things to implement, then two small files.

### 1. A Docker Compose stack

```yaml
# src/test/resources/tck/docker-compose.yaml
services:
  backend:
    image: your-org/your-testbed:1.0.0
    ports:
      - 8080   # control API (see below)
      - 5000   # whatever your provider connects to
```

Conventions the TCK relies on — all overridable:

| Convention | Default | Override |
|---|---|---|
| Service hosting the control API and backend | `backend` | `backendService()` |
| Container-internal control API port | `8080` | `controlPort()` |
| Extra services/ports to expose | none | `additionalExposedPorts()` |

**Never pin host ports.** External ports are mapped dynamically and discovered after startup —
that is why the provider comes from a factory rather than a constant. Pinned ports make the suite
unrunnable in parallel with anything else and collide with a developer's local backend.

The stack may contain any number of extra containers: a toxiproxy, an edge service, a sidecar.
The TCK only cares about the two conventions above.

### 2. A control API on the backend

Your stack must expose a small HTTP control API so the TCK can put the backend into specific
states. The full contract is in [`openapi/control-api.yaml`](src/main/resources/openapi/control-api.yaml),
packaged inside the JAR. Summary:

| Endpoint | Status | Purpose |
|---|---|---|
| `POST /start?config={name}` | **required** | start the backend, seed flags to that config's baseline |
| `POST /stop` | **required** | make the backend unreachable |
| `POST /restart?seconds={n}` | **required** | bounded outage, flag state preserved |
| `POST /change` | **required** | change `changing-flag`'s resolved value |
| `POST /reset` | optional | restore baseline without an outage; falls back to `/start` |
| `GET /healthz` | optional | readiness; falls back to a TCP port check |

Two normative requirements are worth repeating here because getting them wrong is subtle:

> **Never stop or restart a container to simulate an outage.** Testcontainers cannot reliably
> preserve dynamically mapped host ports across a container restart, so a restart silently
> invalidates every provider already pointed at the old port — in some language bindings, and not
> in others, which makes it a portability trap rather than a bug you would catch locally. Simulate
> outages *inside* the running stack: kill the backend process, add a proxy toxic, block the
> socket. The [flagd testbed](https://github.com/open-feature/flagd-testbed) kills and restarts the
> flagd process inside a container that keeps running — that is the reference behaviour.

> **`/start` resets flag state; `/restart` preserves it.** An outage must be observable as a change
> in availability, never as a change in flag values. The TCK relies on this split for scenario
> isolation.

### 3. The canonical flag set

Seed your backend with the flags in [`flags/canonical-flags.json`](src/main/resources/flags/canonical-flags.json).
It is expressed in the flagd flag-definition format because that is the only widely implemented
vendor-neutral format today — the format is not what matters, the keys, types, variants and
resolved values are. Seed them however your backend seeds flags.

Two details are load-bearing:

- **`missing-flag` must not exist.** Its absence is what the `FLAG_NOT_FOUND` scenario tests.
- **No flag has targeting rules.** Every scenario expects reason `STATIC`.

### 4. The test class

```java
public class MyProviderTckTest extends AbstractProviderTckTest {

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
and the harness, and the TCK works out which suite is running from the JUnit test plan. The Compose
lifecycle, port discovery, control API calls, provider registration, event awaiting and teardown all
belong to the TCK. **If you find yourself adding test infrastructure to this class, that is a bug in
the TCK — please open an issue rather than working around it.**

`createUnavailableProvider()` should point at a closed port on localhost, not at your stack — the
stack must stay up, and simulated outages belong to the control API. Give it a short connection
deadline; the scenario allows a bounded time for the error event and a 30-second connect timeout
will not make it.

#### Several provider modes

A provider with more than one transport writes **one class per mode and nothing else** — no
registration, no system property, no build configuration. Each class is its own suite, each gets its
own Compose stack, and they can share a base class:

```java
abstract class AbstractMyProviderTckTest extends AbstractProviderTckTest {
    protected abstract Mode mode();
    // composeFile(), createProvider(), capabilities() ... shared here
}

public class MyProviderRemoteTckTest extends AbstractMyProviderTckTest {
    @Override protected Mode mode() { return Mode.REMOTE; }
}

public class MyProviderInProcessTckTest extends AbstractMyProviderTckTest {
    @Override protected Mode mode() { return Mode.IN_PROCESS; }
}
```

This is how flagd covers RPC and in-process — see
[`AbstractFlagdTckTest`](../../providers/flagd/src/test/java/dev/openfeature/contrib/providers/flagd/e2e/AbstractFlagdTckTest.java).
Abstract classes are not run, so an intermediate base is safe.

Note that per-mode differences may include timing, not just wiring: flagd's in-process resolver
syncs the whole ruleset before reporting ready, so it needs a longer initialisation deadline than
its RPC mode. Give a connecting provider a generous deadline and an intentionally unreachable one a
short deadline — the failure scenarios assert that failure is reported *promptly*.

<details>
<summary>Fallback: <code>ServiceLoader</code> registration</summary>

Suite discovery relies on the JUnit Platform auto-registering `TckSuiteListener` (declared in this
JAR's `META-INF/services/org.junit.platform.launcher.TestExecutionListener`), which Surefire, Gradle
and IDEs all do by default. If your launcher disables listener auto-registration, register the
harness explicitly instead at
`src/test/resources/META-INF/services/dev.openfeature.contrib.tools.providertck.ProviderTckHarness`,
and if you register more than one, select between them with
`-Dopenfeature.tck.harness=MyProviderRemoteTckTest`.

</details>

## Adding your own scenarios

A provider with features of its own — flagd's `fractional` targeting, a vendor's proprietary
evaluation mode — extends the suite rather than maintaining a second one. Two files, no annotations:

```
src/test/resources/tck-extensions/fractional.feature
src/test/java/openfeature/tck/extensions/FractionalSteps.java   // package openfeature.tck.extensions
```

That is the whole extension point. Both are already selected by `AbstractProviderTckTest`, so your
scenarios run **inside** the suite: same Compose stack, same `@BeforeAll`, same control API, same
conformance report. Step classes may take `TckState` as a constructor argument exactly as the
canonical steps do, and reach the control API and the backend endpoint through `TckRuntime.get()`.
Canonical steps are on the glue path too, so an extension scenario can open with `Given a stable
provider` and go on to whatever is specific to your provider.

The alternative — your own Cucumber runner — is a second backend lifecycle to start and a second copy
of this suite's configuration to keep in step with it.

**Why `tck-extensions/` and not `features/`.** Two classpath roots holding the same directory are
scanned additively; two holding the same directory *and* the same file name are not — one wins
silently and the other file is never read. A `features/errors.feature` in your test resources would
therefore *replace* the canonical file, and the suite would report success having run yours. The
extension directory has a different name so that collision cannot be reached by accident. `features/`
is the canonical set and belongs to the specification; extensions are yours. If a scenario is
portable across providers, send it to the TCK rather than keeping it as an extension.

The directory is shipped in this JAR containing only a README, because a classpath resource selector
naming a resource that exists on no classpath root is a hard discovery error rather than an empty
selection. An adopter who extends nothing therefore still resolves it, and pays nothing for the glue
package either — Cucumber tolerates a glue package that does not exist.

### The suite's configuration as constants

`ProviderTck` names every value the suite's annotations carry, so that an adopter who does write a
`@ConfigurationParameter` composes rather than copies:

```java
@ConfigurationParameter(key = Constants.GLUE_PROPERTY_NAME, value = ProviderTck.ALL_GLUE + ",com.vendor.steps")
```

| Constant | Value |
|---|---|
| `ProviderTck.FEATURES` | `features` — the canonical set, reserved |
| `ProviderTck.EXTENSIONS` | `tck-extensions` — where yours go |
| `ProviderTck.GLUE` | the canonical step definitions package |
| `ProviderTck.EXTENSION_GLUE` | `openfeature.tck.extensions` |
| `ProviderTck.ALL_GLUE` | both, comma-separated — what the suite runs with |
| `ProviderTck.PLUGINS`, `PARALLEL_EXECUTION_ENABLED`, `FEATURE_EXECUTION_MODE`, `OBJECT_FACTORY` | the rest of the Cucumber configuration |

An annotation value has to be a compile-time constant, so a method call would not compile there;
constant concatenation does. If you add a glue package this way, keep `ProviderTck.GLUE` in the
value — dropping it makes every canonical step undefined.

## The canonical set cannot be reduced

Extending the suite is safe by convention. Shrinking it is what a conformance suite has to prevent,
because a run that asks twenty-seven of the twenty-nine questions and reports success is
indistinguishable, in every artifact it produces, from one that asked all twenty-nine.

`CanonicalScenarioGuard` is an ordinary JUnit test that the suite selects, and it fails the build if
this run is set up to execute less than the canonical set:

- a feature file added to `features/`, or shadowing a canonical one — the selected scenarios no
  longer match what this artifact ships, which it reads from its own JAR rather than through the
  classpath
- `cucumber.filter.tags` or `cucumber.filter.name` — Cucumber applies these by skipping scenarios at
  execution, so the run is filtered however the plan looks
- selectors or glue overridden in your `junit-platform.properties`

It checks the setup rather than counting afterwards: both the discovered plan and the run's filter
configuration are settled before the first scenario, so the check needs no Compose stack and takes no
measurable time. Where its result appears in the run depends on the order the JUnit Platform executes
the suite's two engines in, which is not specified. Extension scenarios are ignored: the check is
defined over `features/` alone.

Narrowing a run legitimately is what `capabilities()` is for — those scenarios are reported as
skipped with a reason, which a filtered scenario is not. To filter anyway while debugging, set
`-Dprovider.tck.partial=true` (or `PROVIDER_TCK_PARTIAL`). The guard then reports itself as
**skipped** rather than passed, so the run states that its canonical set was not verified.

What the guard does not establish is that the canonical files contain what they should — a
replacement placing its scenarios on the same lines would satisfy it. That is covered better
elsewhere: the results stream carries the `source` of every feature that executed, and
`tck.specRevision` says which revision it should match.

## Declaring capabilities

Not every provider implements every optional part of the spec. Scenarios that exercise an optional
capability carry a tag; declare which ones you support and the rest are reported as **skipped**,
with the reason printed. They are never silently passed — a conformance suite that quietly goes
green on scenarios it did not run is worse than no suite at all.

| Capability | Tag | Meaning |
|---|---|---|
| `LIFECYCLE` | `@lifecycle` | performs an initialisation that reaches its backend, with an observable outcome |
| `EVENTS` | `@events` | emits lifecycle events at all |
| `STALE` | `@stale` | enters `STALE` and emits `PROVIDER_STALE` on backend loss |
| `CONFIGURATION_CHANGE` | `@configuration-change` | detects config changes, emits `PROVIDER_CONFIGURATION_CHANGED` |
| `OBJECT` | `@object` | supports structured flag values |
| `UNAVAILABLE_INIT` | `@unavailable` | reports an error state instead of hanging on a dead backend |
| `NUMERIC_COERCION` | `@numeric-coercion` | coerces between integer and float only when lossless, else `TYPE_MISMATCH` |
| `TARGETING` | `@targeting` | reserved, **not declarable** — no scenarios yet |
| `CACHING` | `@caching` | reserved, **not declarable** — no scenarios yet |

The default is every *declarable* capability. **Narrow it, do not widen it**: start from the
default, run the suite, and remove only what your provider genuinely cannot do.

```java
@Override
public Set<Capability> capabilities() {
    return Capability.declarableExcept(Capability.STALE);
}
```

The reserved entries are part of the vocabulary so that every language's TCK spells the same
property the same way, but no scenario carries their tag — so declaring one cannot produce a skip,
cannot be contradicted by any result, and tells a reader a capability was verified when nothing
examined it. Declaring one **fails the run**, with a message naming the tag.

That is a rule about an accident rather than about intent: `EnumSet.complementOf(EnumSet.of(X))`
reads as "everything except X" and in fact means "every other enum constant", reserved tags
included. The flagd suite said exactly that and published `"declared": [..., "@targeting",
"@caching"]` for two capabilities nobody had claimed. `Capability.declarable()` and
`Capability.declarableExcept(...)` are the forms that mean what the first one looks like.

A note on `LIFECYCLE` vs `EVENTS`: they look like the same thing and are not. `EVENTS` says the
provider emits events; `LIFECYCLE` says there is a real initialisation behind them. The SDK's
`FeatureProviderStateManager` emits `PROVIDER_READY`/`PROVIDER_ERROR` around `initialize` for *any*
provider, `EventProvider` or not — so a provider that does no initialisation of its own reaches
`READY` exactly as `NoOpProvider` would, and gating the readiness scenario on `EVENTS` would pass it
vacuously. Conversely a stateless provider such as OFREP genuinely initialises against a backend
while emitting no events of its own, and would have been excluded. Declare `LIFECYCLE` only if
initialisation actually talks to the backend; a provider with nothing to reach — an in-memory
provider, or a facade over other providers — should not declare it however many events it emits.

A note on `NUMERIC_COERCION`: unlike the others it is not an optional feature. The rule is that
coercion between integer and float is permitted **when it is lossless** and must fail with
`TYPE_MISMATCH` **when it is not** — `10.0` requested as an integer must succeed, `0.5` must not.
Narrowing `0.5` to `0` loses information silently, which is the worst failure mode for a feature
flag, because the application sees a plausible value and no error. It is a capability only so a
provider with this defect can adopt the TCK today and see the gap reported explicitly. Not
declaring it is an admission of a known bug. **The flagd provider currently does not declare it**,
in either RPC or in-process mode — see
[`AbstractFlagdTckTest`](../../providers/flagd/src/test/java/dev/openfeature/contrib/providers/flagd/e2e/AbstractFlagdTckTest.java)
and [flagd#1996](https://github.com/open-feature/flagd/issues/1996).

Two things the tag does not cover, both open in Appendix F rather than fixed here:

- **The lossless case has no scenario.** Only the lossy half is tested, because the canonical flag
  set contains no integral float to ask the other half of, and adding one changes the flag set for
  every language at once. A provider that wrongly rejects `10.0` as an integer declares this
  capability and passes.
- **Accessor width is unmodelled.** The [numeric coercion
  ADR](https://github.com/open-feature/flagd/blob/main/docs/architecture-decisions/numeric-coercion.md)
  distinguishes a 64-bit integer accessor from a 32-bit one — flagd's own testbed tags the latter
  `@int32-bounded` — and neither Appendix F nor this suite has anything equivalent.

### Saying that a withheld capability is a defect

Narrowing `capabilities()` reads the same way in the results whether you did it to describe a
limitation or to work around a bug: the scenarios are skipped either way, and nothing in the run can
tell the two apart. Declare a `KnownDeviation` when it is the latter.

```java
@Override
public List<KnownDeviation> knownDeviations() {
    return List.of(KnownDeviation.tracked(
            Capability.NUMERIC_COERCION,
            "https://github.com/open-feature/java-sdk-contrib/issues/1234",
            "float-flag through the integer API returns 0 with no error code"));
}
```

Use `KnownDeviation.untracked(...)` when there is no issue to point at yet. That is still worth
reporting — naming the defect is what separates it from a choice — but an issue link is better.
Empty is the default, and it is silence rather than a claim of having none.

## Tuning timeouts

How fast a provider notices a backend change differs by orders of magnitude between transports: a
streaming provider sees a configuration change in milliseconds, a provider polling every 30 seconds
needs most of a poll interval. Every await timeout is therefore overridable.

| Method | Default | What it bounds |
|---|---|---|
| `eventTimeout()` | 12s | waiting for a provider event |
| `readyTimeout()` | 30s | waiting for a provider to reach a lifecycle state |
| `startupTimeout()` | 60s | bringing the Compose stack up |
| `settleTime()` | 50ms | pause after a control API call |

```java
@Override
public Duration eventTimeout() {
    return Duration.ofSeconds(45);   // we poll every 30s
}
```

Set `eventTimeout()` to comfortably exceed your worst-case detection latency, or the suite reports
timeouts that are really just impatience. Scenarios that assert promptness as part of their point
use the explicit `within {int}ms` step, which always wins.

## Running it

```bash
mvn test -Dtest=MyProviderTckTest
```

Scenarios run **serially** and the suite enforces this, overriding any
`cucumber.execution.parallel.enabled=true` in your module's `junit-platform.properties`. Control API
state is global to the Compose stack, so concurrent scenarios corrupt each other — one scenario's
`/start` restarts the backend underneath another's disconnect assertion. The symptom looks like a
flaky provider rather than a broken test, which is exactly why it is enforced rather than
documented.

The Compose stack starts once per suite and is never restarted. Scenario isolation comes from the
control API.

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


## Relationship to the flagd test harness

The step vocabulary is inherited from the
[flagd test harness](https://github.com/open-feature/test-harness) wherever it was already
provider-neutral, so flagd's existing feature files port with a near-zero diff and the step
definitions stay familiar. Only genuinely flagd-specific wording was renamed:

| flagd test harness | Provider TCK | Why |
|---|---|---|
| `Given a stable flagd provider` | `Given a stable provider` | drops the vendor name |
| `Given a unavailable flagd provider` | `Given a unavailable provider` | drops the vendor name |

Everything else is unchanged: `a <type>-flag with key ... and a default value ...`,
`the flag was evaluated with details`, `the resolved details value should be "..."`,
`the reason should be ...`, `the variant should be ...`, `the error-code should be ...`,
`a <kind> event handler`, `the <kind> event handler should have been executed[ within <n>ms]`,
`the connection is lost[ for <n>s]`, `the flag was modified`,
`the flag should be part of the event payload`, `the client should be in <state> state`.

Three steps are new:

| Step | Why it was added |
|---|---|
| `When the connection is restored` | the flagd harness only has the self-healing `lost for {int}s` form, which cannot express "assert stale, *then* reconnect" — the reconnect races the assertion |
| `When the resolved value is remembered` / `Then the resolved details value should have changed` | the control API only requires that `/change` changes `changing-flag`'s value, not which value it changes to; asserting a delta keeps the scenario vendor-neutral |
| `Then no exception should have been thrown` | makes the "never throws" half of the error contract explicit rather than implicit in a step failure |

## Where these artifacts should live

The feature files, the control API spec and the canonical flag set are **not Java artifacts**. They
are language-agnostic definitions of the provider contract that every language's TCK must agree on
byte for byte, and that backend vendors implement in whatever language their testbed is written in.

They belong in the OpenFeature [spec repository](https://github.com/open-feature/spec), with this
module as their Java delivery vehicle. The three travel together by necessity: a feature file that
evaluates `boolean-flag` is meaningless without the flag definition, and a disconnect scenario is
meaningless without the endpoint that produces the disconnect.

They live here for now only because the PoC had to start somewhere. Moving them changes nothing for
consumers — the features stay on the classpath and stay inside the JAR.

## Known gaps

- **Evaluation context passthrough.** The TCK builds evaluation contexts but cannot assert the
  context *reached* the backend intact. That needs an echo operation on the control API — something
  like `GET /last-evaluation` returning the request the backend last received. Until then, a
  provider that silently drops the context passes.
- **Targeting and bucketing.** Out of scope by design: that is backend evaluation logic. The
  `@targeting` tag is reserved for context-passthrough scenarios once the gap above is closed, and
  is not declarable until they exist.
- **Caching.** Whether a stale provider keeps serving last-known values during an outage depends on
  whether it holds a local copy of the ruleset. The `@caching` tag is reserved; no scenarios yet,
  and so not declarable.
- **Lossless numeric coercion.** `@numeric-coercion` tests only the lossy half of its rule. The
  canonical flag set holds no integral float, so there is nothing to ask "must `10.0` resolve as an
  integer?" of, and a provider that wrongly answers no still passes. Closing it means adding a flag
  to the canonical set, which changes it for every language at once.
- **Integer accessor width.** flagd's numeric coercion ADR distinguishes a 64-bit integer accessor
  from a 32-bit one, and tags the latter `@int32-bounded` in its own testbed. Neither this suite nor
  Appendix F models width at all, and it is a real source of cross-language disagreement.
- **Hooks.** Not covered.
- **Flag metadata.** The flagd harness has metadata scenarios; they are not yet ported.
- **Multi-suite JVMs.** `TckRuntime` is static, so TCK suites run one at a time within a JVM fork.
  Several suites in one fork is fine — they run sequentially, each with its own Compose stack — but
  they cannot run concurrently.
- **Scenario coverage is a representative subset**, covering each architectural mechanism once
  rather than exhaustively.

## Contributing

See the repository [CONTRIBUTING.md](../../CONTRIBUTING.md). New scenarios should be portable
across providers: if a scenario can only pass against one vendor's backend semantics, it belongs in
that provider's own suite, not here.
