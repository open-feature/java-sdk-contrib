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
  <artifactId>tck</artifactId>
  <version>0.1.0</version>
  <scope>test</scope>
</dependency>
```
<!-- x-release-please-end-version -->

Requires Java 11+ and JUnit 5. A working Docker daemon is needed only for providers with an
external backend — see [Which base class to extend](#which-base-class-to-extend).

### Testcontainers, for containerised adopters only

`ContainerizedProviderTckTest` owns a `ComposeContainer`, so this artifact compiles against
Testcontainers — but it declares the dependency `provided` and `optional`, so **it is not
transitive**. A containerised adopter adds it itself:

```xml
<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>testcontainers</artifactId>
  <version>2.0.4</version>
  <scope>test</scope>
</dependency>
```

That is one line for the adopters that need it, and it keeps Testcontainers off the test classpath of
every backend-less adopter — in-memory, environment-variable, file-based — which would otherwise
resolve it for a class they never load. It also lets an adopter stay on the Testcontainers major its
other suites already use instead of inheriting ours.

### OpenFeature SDK compatibility

The TCK declares `dev.openfeature:sdk` as a **`provided` version range** (`[1.21.0,1.99999)`),
inherited from this repository's parent POM. It never pins an SDK version.

That is deliberate. A conformance suite that forces an SDK upgrade before you can run it is a
conformance suite nobody runs. Your build keeps whatever SDK version it already resolves; the TCK
uses only long-stable API — `OpenFeatureAPI`, `Client`, typed evaluation, `ProviderEvent`,
`ProviderState`.

## What it tests, and what it does not

**In scope — the provider contract:**

- mapping backend responses onto typed resolution details (value, reason, error code), with no error
  message on a success path — and the variant where the backend names one, which is gated on
  `@variants` because Requirement 2.2.4 is a `SHOULD` and `types.md` types the field optional
- keeping the integer and float types distinct; that `false`, `0` and `""` are values, not absences;
  integer precision to 2^31 − 1
- error handling: type mismatch and unknown flag return the code default, report the right error
  code, and never throw
- lifecycle: reaching `READY`, settling into `ERROR` against an unreachable backend, and a shutdown
  that can be repeated, returns promptly when the backend is gone, and is undone by initialising again
- events: `PROVIDER_READY`, `PROVIDER_ERROR`, `PROVIDER_STALE`, `PROVIDER_CONFIGURATION_CHANGED`
- that a signalled configuration change is actually applied on re-evaluation
- that the provider identifies itself by a non-empty metadata name
- that supplying an evaluation context does not disturb an untargeted resolution, and — gated on
  `@targeting` — that a matching context resolves the targeted variant
- gated on `@disabled-flags`, that a flag disabled in the management system resolves to the code
  default rather than to its configured value, and without an error. Gated because the answer depends
  on where the substitution happens: a provider that evaluates locally holds the caller's default and
  can return it, one whose backend decides never sends it and cannot

**Out of scope — not the provider's contract:**

- backend evaluation logic, bucketing and rule-language correctness. Every enabled flag in the
  canonical set except `targeting-key-flag` resolves to its default variant whatever the context, so
  what is under test is the provider's mapping of a response, not the backend's decision. That one
  carries the one rule, and it is there to prove the context reached the backend rather than to test
  how the backend evaluated it. The four `disabled-*` flags are the only ones whose state is not
  `ENABLED`; they resolve to nothing at all.
- the provider↔backend wire protocol. How you talk to your backend is your business.
- SDK behaviour. That belongs to the SDK's own test suite.

## Which base class to extend

Two, and the choice is made by one question: **does your provider talk to something outside the
JVM?**

| | Extend | Backend control | You supply |
|---|---|---|---|
| Provider has an external backend | `ContainerizedProviderTckTest` | `HttpBackendControl`, over the HTTP control API | a Compose stack, a control API, a test class |
| Provider has no backend — in-memory, environment variables, a local file | `ProviderTckTest` | an in-process `BackendControl` | a test class |

`ContainerizedProviderTckTest` is the normal case and everything in [Adopting it](#adopting-it)
below describes it. It extends `ProviderTckTest` and adds the Compose lifecycle, port discovery and
control API client on top.

### In-process control is for backend-less providers only

Step definitions never touch a backend directly. They go through one interface, `BackendControl`,
which is what lets the same Gherkin run against a container over HTTP and against an in-memory
provider manipulated in the same JVM.

That seam is not an invitation to skip the control API. **If your provider has an external backend,
use `HttpBackendControl` via `ContainerizedProviderTckTest`.** The control API described in
[`openapi/control-api.yaml`](src/main/resources/openapi/control-api.yaml) is the normative contract
for those providers, and it is the whole basis of a portable conformance claim: another language's
TCK drives the same endpoints against the same stack and must get the same answers.

A custom in-JVM `BackendControl` that reaches an external backend through a side channel — a
test-only admin client, a shared database handle, a static hook inside the provider — bypasses that
contract. It will pass, and it will prove nothing, because the path it exercised is not the path the
contract describes.

In-process control exists for providers that have **nothing to contract with**, where "the backend"
is a data structure in the same JVM. For those, flag operations are map updates and a configuration
change is the provider's own update mechanism emitting its own event.

### Adopting it without a backend

`InProcessBackendControl` implements this for the SDK's `InMemoryProvider`, seeded with the
canonical flag set. The entire adoption is three methods:

```java
public class MyProviderTckTest extends ProviderTckTest {

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

One object backs both factory methods because in-process the flag store and the provider are the
same thing: `changeFlag()` has to reach the live provider instance to emit an event from it.

**Connection control does not apply**, and the capability declaration is where you say so rather
than stubbing it out. An in-memory provider has no connection to lose, so
`InProcessBackendControl` leaves `disconnect()` and `reconnect()` unimplemented —
they throw. Leaving `STALE` and `UNAVAILABLE_INIT` out of `capabilities()` is what keeps that
honest: the scenarios needing them are skipped before any step can reach an unsupported operation.

Get that pairing wrong — declare `STALE` against a control that cannot disconnect — and you get an
`UnsupportedOperationException` naming the fix, not a silent pass. That is deliberate. A
`BackendControl` may throw `UnsupportedOperationException` for operations it does not support, and
reaching one from a scenario that actually ran is a **test-configuration bug**, never a skip.

### The TCK's own self-tests

Three suites in this module are exactly the class above, and all three run with no Docker in well
under a second. They are the reference adoption, and they are the fast CI canary.

[`InMemoryProviderTckTest`](src/test/java/dev/openfeature/contrib/tools/tck/InMemoryProviderTckTest.java)
runs the full applicable suite against the SDK's `InMemoryProvider` — of the 56 scenarios (outline
rows counted individually), 42 pass and 14 are skipped by capability: the six `@lifecycle` ones — one
of which also carries `@reinitialization`, and is skipped for the first of the two — the `@stale`
one, the three `@numeric-coercion` ones, the `@large-integers` one and the three `@targeting` ones.
It declares `VARIANTS`, because `InMemoryProvider` does name the variant it served, so the gated
variant outline runs rather than being skipped. It declares `DISABLED_FLAGS` too, on the same kind of
evidence: the provider honours a flag's state, so the four `disabled-*` flags resolve to nothing, the
caller's default stands in with no error code, and all four rows of that outline pass. It does not
declare `TARGETING`: the provider reads a flag's `variants` and `defaultVariant` and evaluates no
rules, so `targeting-key-flag`'s `targeting` member is inert and a matching context resolves `miss`
like any other. It does not declare `NUMERIC_COERCION`, because `InMemoryProvider` keeps the two
numeric types strictly apart in both
directions — it refuses `10.0` as an integer and `10` as a float exactly as it refuses `0.5` — and the
tag requires the lossless direction too. That is a choice the SDK's reference provider is entitled to,
not a defect; see the class javadoc.

[`ControllableProviderTckTest`](src/test/java/dev/openfeature/contrib/tools/tck/ControllableProviderTckTest.java)
runs it against a provider with a **real initialisation**, and it is the only Docker-free cover the
`@lifecycle` feature has. `InMemoryProvider` cannot provide it: its constructor is handed the whole
flag set, so `initialize()` records a state and `shutdown()` releases nothing observable, and
running those scenarios against it would establish nothing — which is exactly why the suite above
withholds `LIFECYCLE`. The consequence was that shutdown, double shutdown, shutdown against a dead
backend and initialise-again had coverage only inside a containerised provider suite, where a break
in them reads as a provider defect rather than a TCK one. `ControllableProvider` acquires its flag
store at `initialize()` time from a store that may refuse it, so it declares `LIFECYCLE`,
`REINITIALIZATION` and `UNAVAILABLE_INIT` and all six `@lifecycle` scenarios run. Of the 14 the
in-memory suite skips, only 8 remain: the three `@numeric-coercion`, the three `@targeting`, the
`@large-integers` one and the `@stale` one — `@stale` because an in-JVM store can refuse an
initialisation but cannot take a connection away from a running provider and hand it back, so
`disconnect()` stays at its throwing default. That is the one capability still without Docker-free
coverage.

The in-JVM store is not a licence for a provider that does have a backend to test itself this way;
see [In-process control is for backend-less providers
only](#in-process-control-is-for-backend-less-providers-only). `InMemoryProviderTckTest` stays the
reference adoption an adopter copies, because it is written against the published
`InProcessBackendControl` and the SDK's own provider.

[`MultiProviderTckTest`](src/test/java/dev/openfeature/contrib/tools/tck/MultiProviderTckTest.java)
runs it against `MultiProvider` wrapping **one** `InMemoryProvider`. A provider that delegates is
still a provider, and delegation is where the contract is easiest to drop: a variant that does not
survive the hop, a reason rewritten, an error code flattened, an event that never arrives. With a
single child the correct answer is precisely what the in-memory suite already asserts, so any
difference between the two suites is attributable to `MultiProvider` and nothing else.

That suite has already paid for itself. It does **not** declare `CONFIGURATION_CHANGE`, because
`MultiProvider` extends `EventProvider` but never subscribes to its children — a child's
`PROVIDER_CONFIGURATION_CHANGED`, `PROVIDER_ERROR` and `PROVIDER_STALE` are all swallowed. Wrapping
a provider in a multi-provider silently costs you those events, with nothing in the API to hint at
it. That is a known SDK gap,
[open-feature/java-sdk#1882](https://github.com/open-feature/java-sdk/issues/1882) (gap 1, High),
which the suite reproduced from the outside — the gap was originally found by hand-comparing
implementations against the js-sdk reference. Everything else survives delegation unchanged.

Two more tests are not suites at all, because what they guard is invisible from inside a scenario.
[`InProcessBackendControlTest`](src/test/java/dev/openfeature/contrib/tools/tck/InProcessBackendControlTest.java)
calls the unsupported operations directly, so a connection operation that quietly did nothing cannot
pass as a skip.
[`HttpBackendControlTest`](src/test/java/dev/openfeature/contrib/tools/tck/HttpBackendControlTest.java)
stubs the control API with the JDK's own `com.sun.net.httpserver.HttpServer` — no Docker, nothing off
loopback — and asserts the request sequence in order: that `/reset` is preferred and `/start` is the
fallback, that an unimplemented `/reset` is probed **once per suite** and the answer cached, and that
the scenario after a `disconnect()` uses `/start` rather than `/reset`. All three are normative in
`openapi/control-api.yaml`, all three are decided in code no scenario can observe, and a control that
got any of them wrong would let scenarios run against the previous one's backend state and report the
results as conformance.

## Adopting it

This section describes a provider with an external backend — the common case.
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

The whole compose contract, which is the same eight concepts with the same defaults in every
language's TCK:

| Concept | Required | Default | Java |
|---|---|---|---|
| Compose file | yes | — | `File composeFile()` — resolved relative to the Maven module directory |
| Backend service | no | `backend` | `String backendService()` — the service hosting both the control API and the backend |
| Backend ports | yes | — | `List<Integer> backendPorts()` — container-internal ports the *provider* connects to. Do not list the control port; it is exposed automatically |
| Control port | no | `8080` | `int controlPort()` |
| Additional ports | no | none | `Map<String, List<Integer>> additionalPorts()` — extra service → ports, resolved through the endpoint by service name |
| Backend configuration | no | `default` | `String backendConfiguration()` — the backend configuration name passed to `POST /start` |
| Startup timeout | no | 60s | `Duration startupTimeout()` — the stack and its control API becoming reachable |
| Endpoint | — | — | `BackendEndpoint` — `host()` and `port(internalPort)`, optionally qualified by service |

`backendConfiguration()` names a configuration the **backend** understands. It is not
`configuration()`, which names the mode of the **provider** — see [Naming the configuration under
test](#naming-the-configuration-under-test). The two words were the same in three of the four
languages' first drafts, and telling them apart is the reason this one is spelled out.

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
| `POST /change` | **required** | change `changing-flag`'s resolved value |
| `POST /reset` | optional | restore baseline without an outage; falls back to `/start` |
| `POST /restart?seconds={n}` | optional | bounded outage, flag state preserved — **no shipped scenario calls it** |
| `GET /healthz` | optional | readiness; falls back to a TCP port check |

`/restart` is optional and this suite binds no method to it. The disconnect/reconnect scenario is
written as an *unbounded* outage — `the connection is lost`, then `the connection is restored` —
which is `/stop` followed by `/start`, because a self-healing outage cannot express "assert the
provider is stale, and only then reconnect". It stays specified because a future `@caching` scenario
asserting what a stale provider serves *during* an outage needs exactly its flag-state preservation,
which `/start`-on-reconnect does not give.

Two normative requirements are worth repeating here because getting them wrong is subtle:

> **Never stop or restart a container to simulate an outage.** Testcontainers cannot reliably
> preserve dynamically mapped host ports across a container restart, so a restart silently
> invalidates every provider already pointed at the old port — in some language bindings, and not
> in others, which makes it a portability trap rather than a bug you would catch locally. Simulate
> outages *inside* the running stack: kill the backend process, add a proxy toxic, block the
> socket. The [flagd testbed](https://github.com/open-feature/flagd-testbed) kills and restarts the
> flagd process inside a container that keeps running — that is the reference behaviour.

> **`/start`, `/change` and `/reset` must not return until the new state is being served.** That
> promise is about the *backend*: a fresh evaluation against it must already resolve the new value
> when the call returns. How long the provider under test takes to notice is a property of its
> transport and is what `eventTimeout()` bounds. Confusing the two makes the provider's detection
> latency unmeasurable, because the clock starts before there is anything to detect — and it is why
> nothing in this suite sleeps after a control call.

### 3. The canonical flag set

Seed your backend with the flags in [`flags/canonical-flags.json`](src/main/resources/flags/canonical-flags.json).
It is expressed in the flagd flag-definition format because that is the only widely implemented
vendor-neutral format today — the format is not what matters, the keys, types, variants and
resolved values are. Seed them however your backend seeds flags.

Four details are load-bearing:

- **`missing-flag` must not exist.** Its absence is what the `FLAG_NOT_FOUND` scenario tests.
- **Only `targeting-key-flag` has a targeting rule.** Every other flag resolves to its default
  variant whatever the evaluation context, which is what lets the untargeted scenarios expect reason
  `STATIC`; seeding targeting onto any other flag breaks them. Its rule is specified by behaviour —
  resolve `hit` when the targeting key is exactly `5c3d8535-f81a-4478-a6d3-afaa4d51199e`, `miss`
  otherwise — so express it however your backend expresses targeting. The flag, its variants and the
  uuid are flagd-testbed's own, so a backend serving that harness already serves this one. A backend
  that cannot carry a rule leaves `TARGETING` undeclared and the three scenarios are skipped.
- **`boolean-zero-flag`, `integer-zero-flag` and `string-zero-flag` resolve to `false`, `0` and
  `""` on purpose.** A seeding step that treats them as unset and drops them turns the falsy-value
  scenarios into `FLAG_NOT_FOUND` failures that look like provider defects. These names, and their
  `zero`/`non-zero` variants, are the ones Appendix B's SDK suite already uses, so a backend that
  serves that flag set already serves these.
- **`integral-float-flag` is a float and `huge-integer-flag` is an integer.** Seeding `10.0` as `10`
  makes the lossless-coercion scenario pass without coercing anything; seeding `9007199254740991`
  through a float rounds it.

Read the file rather than retyping it. `$comment` members are documentation and may be ignored
wherever they appear; everything else is the contract. This is what
[`InProcessBackendControl`](src/main/java/dev/openfeature/contrib/tools/tck/InProcessBackendControl.java)
does — it decodes the packaged copy through
[`CanonicalFlags`](src/main/java/dev/openfeature/contrib/tools/tck/CanonicalFlags.java)
rather than restating the set in Java, because a second copy inside the TCK drifts from the spec the
same way an adopter's would, and when it does the in-memory self-tests go green against the wrong
baseline.

### 4. The test class

```java
public class MyProviderTckTest extends ContainerizedProviderTckTest {

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
abstract class AbstractMyProviderTckTest extends ContainerizedProviderTckTest {
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

This is how the flagd provider covers RPC and in-process. Abstract classes are not run, so an
intermediate base is safe.

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
`src/test/resources/META-INF/services/dev.openfeature.contrib.tools.tck.ProviderTckHarness`,
and if you register more than one, select between them with
`-Dopenfeature.tck.harness=MyProviderRemoteTckTest`.

</details>

## Adding your own scenarios

A provider with features of its own — flagd's `fractional` targeting, a vendor's proprietary
evaluation mode — extends the suite rather than maintaining a second one. Two files, no annotations:

```
src/test/resources/extensions/fractional.feature
src/test/java/openfeature/tck/extensions/FractionalSteps.java   // package openfeature.tck.extensions
```

That is the whole extension point. Both are already selected by `ProviderTckTest`, so your scenarios
run **inside** the suite: same backend lifecycle, same `@BeforeAll`, same `BackendControl`. Step
classes may take `TckState` as a constructor argument exactly as the canonical steps do, and reach
the backend control and the backend endpoint through `TckRuntime.get()`. Canonical steps are on the
glue path too, so an extension scenario can open with `Given a stable provider` and go on to whatever
is specific to your provider.

The alternative — your own Cucumber runner — is a second backend lifecycle to start and a second copy
of this suite's configuration to keep in step with it.

**Why `extensions/` and not `gherkin/`.** Two classpath roots holding the same directory are
scanned additively; two holding the same directory *and* the same file name are not — one wins
silently and the other file is never read. A `gherkin/errors.feature` in your test resources would
therefore *replace* the canonical file, and the suite would report success having run yours.
`gherkin/` and `extensions/` being two distinct directories means that collision cannot be reached
by accident. `gherkin/` is the canonical set and belongs to the specification; extensions are yours.
If a scenario is portable across providers, send it to the TCK rather than keeping it as an
extension.

Both names are Appendix F's. It identifies a canonical feature by its path relative to the spec's
asset directory — `gherkin/errors.feature` — and reserves the prefix `extensions/` for an adopter's
own, so the URIs a run reports (`classpath:gherkin/errors.feature`,
`classpath:extensions/fractional.feature`) partition the same way here as in every other language's
TCK. Comparison is on the path after the URI scheme; the `classpath:` prefix is this runner's and is
not part of the identity.

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
| `ProviderTck.FEATURES` | `gherkin` — the canonical set, reserved |
| `ProviderTck.EXTENSIONS` | `extensions` — where yours go |
| `ProviderTck.GLUE` | the canonical step definitions package |
| `ProviderTck.EXTENSION_GLUE` | `openfeature.tck.extensions` |
| `ProviderTck.ALL_GLUE` | both, comma-separated — what the suite runs with |
| `ProviderTck.PLUGINS`, `PARALLEL_EXECUTION_ENABLED`, `FEATURE_EXECUTION_MODE`, `OBJECT_FACTORY` | the rest of the Cucumber configuration |

An annotation value has to be a compile-time constant, so a method call would not compile there;
constant concatenation does. If you add a glue package this way, keep `ProviderTck.GLUE` in the
value — dropping it makes every canonical step undefined.

## Declaring capabilities

Not every provider implements every optional part of the spec. Scenarios that exercise an optional
capability carry a tag; declare which ones you support and the rest are reported as **skipped**,
with the reason printed. They are never silently passed — a conformance suite that quietly goes
green on scenarios it did not run is worse than no suite at all.

| Capability | Tag | Meaning |
|---|---|---|
| `LIFECYCLE` | `@lifecycle` | performs an initialisation that reaches its backend, with an observable outcome |
| `REINITIALIZATION` | `@reinitialization` | can be initialised again after `shutdown` — [Requirement 2.5.2](https://github.com/open-feature/spec/blob/main/specification/sections/02-providers.md) *permits* this rather than requiring it |
| `EVENTS` | `@events` | emits lifecycle events at all |
| `STALE` | `@stale` | enters `STALE` and emits `PROVIDER_STALE` on backend loss — *needs connection control* |
| `CONFIGURATION_CHANGE` | `@configuration-change` | detects config changes, emits `PROVIDER_CONFIGURATION_CHANGED` |
| `OBJECT` | `@object` | supports structured flag values |
| `VARIANTS` | `@variants` | names the variant it resolved — [Requirement 2.2.4](https://github.com/open-feature/spec/blob/main/specification/sections/02-providers.md) is a `SHOULD` and `types.md` types the field optional, so a backend with no variant concept withholds it |
| `DISABLED_FLAGS` | `@disabled-flags` | resolves a flag disabled in the management system to the code default — *needs the substitution to happen where the caller's default is, so a provider whose backend decides cannot hold it* |
| `UNAVAILABLE_INIT` | `@unavailable` | reports an error state instead of hanging on a dead backend — *needs connection control* |
| `NUMERIC_COERCION` | `@numeric-coercion` | coerces between integer and float only when lossless, else `TYPE_MISMATCH` — both directions tested |
| `LARGE_INTEGERS` | `@large-integers` | resolves integers up to 2^53 − 1 exactly; **every Java provider withholds it** — the SDK's integer accessor is a 32-bit `Integer`, so the limit is the language's, not the provider's |
| `TARGETING` | `@targeting` | resolves `targeting-key-flag` differently for a matching evaluation context — *needs a backend that evaluates rules* |
| `CACHING` | `@caching` | reserved, **not declarable** — no scenarios yet |

The default is every *declarable* capability. **Narrow it, do not widen it**: start from the
default, run the suite, and remove only what your provider genuinely cannot do.

`STALE` and `UNAVAILABLE_INIT` are the two that need a backend the provider can be cut off from.
They are what a backend-less provider leaves undeclared — see
[In-process control is for backend-less providers only](#in-process-control-is-for-backend-less-providers-only).
Declaring one against a `BackendControl` that cannot simulate an outage fails the scenario with an
`UnsupportedOperationException` naming the fix, rather than passing it.

```java
@Override
public Set<Capability> capabilities() {
    return Capability.declarableExcept(Capability.STALE);
}
```

A reserved entry is part of the vocabulary so that every language's TCK spells the same property the
same way, but no scenario carries its tag — so declaring it cannot produce a skip, cannot be
contradicted by any result, and tells a reader a capability was verified when nothing examined it.
Declaring one **fails the run**, with a message naming the tag. `CACHING` is the only reserved entry
left: `TARGETING` was reserved until `targeting-key-flag`'s three scenarios arrived, and is an
ordinary declarable capability now.

The other direction fails the run too, and it is the one an adopter will meet first. A reservation
expires the day the specification writes the scenarios it was held open for, and if this package has
not followed, the two rules meet in the worst possible place: the new scenario is skipped for a
capability nobody is permitted to declare — a question put and silently withdrawn, which
[Appendix F](https://github.com/open-feature/spec/blob/main/specification/appendix-f-provider-conformance.md)
calls the unclaimable capability. The report is well-formed and the run is
green, so nothing else would notice. So a scenario carrying a reserved tag **fails**, naming the tag
and saying that the reserved flag on that constant is now the only thing to change. The check reads
Cucumber's parsed tags rather than the feature files as text, which matters more than it sounds:
`gherkin/events.feature` names `@caching` inside a `#` comment explaining what is deliberately not
covered yet, so a text scan would fail every adoption on the day it shipped.

That is a rule about an accident rather than about intent: `EnumSet.complementOf(EnumSet.of(X))`
reads as "everything except X" and in fact means "every other enum constant", reserved tags
included. The flagd suite said exactly that and published `"declared": [..., "@targeting",
"@caching"]` for two capabilities nobody had claimed — back when both were reserved.
`Capability.declarable()` and `Capability.declarableExcept(...)` are the forms that mean what the
first one looks like, and they still exclude `@caching`.

A note on `LIFECYCLE` vs `EVENTS`: they look like the same thing and are not. `EVENTS` says the
provider emits events; `LIFECYCLE` says there is a real initialisation behind them. The SDK's
`FeatureProviderStateManager` emits `PROVIDER_READY`/`PROVIDER_ERROR` around `initialize` for *any*
provider, `EventProvider` or not — so a provider that does no initialisation of its own reaches
`READY` exactly as `NoOpProvider` would, and gating the readiness scenario on `EVENTS` would pass it
vacuously. Conversely a stateless provider such as OFREP genuinely initialises against a backend
while emitting no events of its own, and would have been excluded. Declare `LIFECYCLE` only if
initialisation actually talks to the backend; a provider with nothing to reach — an in-memory
provider, or a facade over other providers — should not declare it however many events it emits.

A note on `REINITIALIZATION`, which is separate from `LIFECYCLE` for a different reason and is worth
reading before you withhold anything else.
[Requirement 2.5.2](https://github.com/open-feature/spec/blob/main/specification/sections/02-providers.md)
says a provider **SHOULD** revert to its uninitialized state after `shutdown`, and its supporting
text adds that *"some providers **may** allow reinitialization from this state"*. Reuse is
**permitted, not required**: a provider that releases its client on shutdown and refuses to be
started again is exercising a choice the specification offers it, so withholding the tag needs no
`KnownDeviation`. The scenario it gates was originally untagged, and therefore mandatory, on the
reading that reverting to the uninitialized state is observable as exactly one thing — being
initialisable again. That inference does not hold, and it cost something: run against the flagd
provider, which keeps `isInitialized` and `isShutDown` as separate flags and refuses `initialize()`
when either is set, the scenario failed and was one step from being filed as a defect against a
provider doing nothing wrong. **A false failure is the mirror image of a vacuous pass.** The tag
still earns its keep in the other direction, for the providers that do offer reuse: releasing the
client on shutdown while leaving an initialised flag set is easy to write, and it leaves the provider
evaluating against a closed connection rather than failing outright.

The general rule behind that, which is worth more than the tag: **never withhold a capability, or
record a deviation, because a scenario failed — first find the numbered requirement and check
whether the specification asks for that behaviour at all.** Three rules in this suite have now been
found asserted more strongly than the spec states them.

A note on `NUMERIC_COERCION`: the rule it tests is **borrowed, not normative**. Coercion between
integer and float is permitted **when it is lossless** and must fail with `TYPE_MISMATCH` **when it
is not** — `10.0` requested as an integer must succeed, `10` requested as a float must succeed, and
`0.5` requested as an integer must not. All three have scenarios and a provider declaring the tag
must satisfy all three; rejecting every float passes the lossy one and fails the other two. The rule
comes from flagd's [numeric coercion
ADR](https://github.com/open-feature/flagd/blob/main/docs/architecture-decisions/numeric-coercion.md);
the specification has a single numeric type and says nothing about a value that does not fit the
accessor it was asked through ([spec#430](https://github.com/open-feature/spec/issues/430)), so a
provider that behaves differently is not violating it. It is still worth saying which kind of
difference it is: narrowing `0.5` to `0` with no error code hands an application a plausible value and
no signal, which is a defect to declare as a `KnownDeviation`, whereas keeping the two types strictly
apart — what `InMemoryProvider` does — is a choice. **The flagd provider does not declare it**, in
either mode, for the first reason — see
[flagd#1996](https://github.com/open-feature/flagd/issues/1996).

A note on `LARGE_INTEGERS`: accessor width is a property of the SDK, not of the provider, and Java's
is 32 bits — `Client.getIntegerDetails` takes and returns an `Integer`, which has no room for
2^53 − 1. So **every Java provider withholds this tag**, and its one scenario is reported as skipped
for an undeclared capability like any other. Put it in your `declarableExcept(...)` list:

```java
return Capability.declarableExcept(Capability.LARGE_INTEGERS, /* whatever else */);
```

That the impossibility is the language's rather than the provider's is recorded once, in
[Appendix F](https://github.com/open-feature/spec/blob/main/specification/appendix-f-provider-conformance.md),
rather than restated in every run: a report has one skip status, carrying its reason, and the
scenario's own tags say what was being asked. Withholding the tag therefore needs no
`KnownDeviation` — it is not a defect. Declaring it is not refused either; the scenario runs and
fails when `TckValues` cannot fit `9007199254740991` into an `Integer`, which is a louder answer than
a rejected declaration. The 32-bit precision scenario (`large-integer-flag`, 2^31 − 1) is untagged and
always runs.

### Saying that a gap is a defect

A `knownDeviations` entry says one thing: **this provider fails to do something it is required to
do.** The requirement has to be a numbered `MUST`, or a rule the implementation bound itself to
elsewhere — flagd's numeric-coercion ADR, say. Where the specification *permits* the choice,
withholding the capability **is** the honest report, and a deviation entry would assert a defect
that does not exist.

It is legitimate in two shapes, and a run's results already tell them apart:

1. **The capability is declared, the scenario runs, and it fails.** *Prefer this.* The failure stays
   visible and the deviation says it is known and why, so a reader sees both the assertion that
   broke and your account of it.
2. **The capability is withheld, and its scenarios skip.** Legitimate only when the provider cannot
   attempt the behaviour at all — there is no connection to lose, no structured value to return — so
   running the scenario would establish nothing. The deviation explains the absence, so a reader can
   tell a defect from a design decision.

Withdrawing a capability *in order to* turn a failing scenario into a skip is the failure mode this
field exists to prevent. If the provider attempts the behaviour and gets it wrong, declare the
capability, let the scenario fail, and record the deviation beside the failure.

```java
@Override
public List<KnownDeviation> knownDeviations() {
    return List.of(KnownDeviation.tracked(
            Capability.NUMERIC_COERCION,
            "https://github.com/open-feature/java-sdk-contrib/issues/1234",
            "float-flag through the integer API returns 0 with no error code"));
}
```

`summary` is **required**: an entry with no summary records that something is wrong without saying
what, which is worth less than the bare skip or failure it accompanies. `issue` is **optional** —
use `KnownDeviation.untracked(...)` when there is nothing to point at yet. That is still worth
declaring, because naming the defect is what separates it from a choice, but an issue link is
better. The capability may be `null`, when the gap is against a mandatory, ungated scenario; it may
not be a [reserved](#declaring-capabilities) one, since no scenario carries the tag and so there is
nothing to deviate from. Empty is the default, and it is silence rather than a claim of having none.

### Naming the configuration under test

`configuration()` is the provider's *configuration*, not its identity: which of its modes this suite
exercised. A provider with two materially different modes — flagd's RPC and in-process resolvers —
runs two suites whose results are not interchangeable, and the name is what keeps them apart.

It defaults to the suite class name, hyphenated and with the JUnit suffix dropped, so
`FlagdInProcessTckTest` becomes `flagd-in-process`. Override it when that does not read well.

### How the backend was driven

`BackendControl.controlApi()` says which of the two contracts a run was conducted under:
`ControlApi.HTTP`, the normative control API, or `ControlApi.IN_PROCESS`, the narrow allowance for a
provider with no backend at all. They serialise as `http` and `in-process`. A claim of `in-process`
for a provider that does have a backend should be treated with suspicion — see [In-process control is
for backend-less providers only](#in-process-control-is-for-backend-less-providers-only).

It is **required and has no default**, which is a deliberate choice and not an oversight:

- The two runs it distinguishes are not the same claim. The same scenarios passing over the control
  API and passing through in-process manipulation of a provider that *does* have a backend prove
  different things, and this is the only field that separates them. An unanswered value is therefore
  not "no claim made" — it is an unfalsifiable one.
- It cannot be inferred from the control's concrete type. `HttpBackendControl` and
  `InProcessBackendControl` answer it themselves, and an adopter with a real backend writes no
  control at all. The only person who implements this interface by hand is the one writing a custom
  control — precisely the case where nothing downstream can guess.
- A `String` would be wider than the report schema's enum, so an implementor could return `"HTTP"`
  and produce a document that fails validation with no local error. `ControlApi` is closed for that
  reason.

## Tuning timeouts

How fast a provider notices a backend change differs by orders of magnitude between transports: a
streaming provider sees a configuration change in milliseconds, a provider polling every 30 seconds
needs most of a poll interval. Every await timeout is therefore overridable.

| Method | Default | What it bounds |
|---|---|---|
| `eventTimeout()` | 12s | waiting for a provider event |
| `readyTimeout()` | 30s | waiting for a provider to reach a lifecycle state |
| `startupTimeout()` | 60s | bringing the Compose stack up, and its control API becoming reachable (`ContainerizedProviderTckTest` only) |

```java
@Override
public Duration eventTimeout() {
    return Duration.ofSeconds(45);   // we poll every 30s
}
```

Set `eventTimeout()` to comfortably exceed your worst-case detection latency, or the suite reports
timeouts that are really just impatience. Scenarios that assert promptness as part of their point
use the explicit `within {int}ms` step, which always wins.

Every entry in that table is a **bound on an await**, and there is deliberately no entry that is a
**pause**. Nothing sleeps after a control API call: a control call returns when the backend has
acted, because that is what the control API promises — `POST /start` blocks until the flags are
evaluable. A fixed pause would cover that window whether or not the promise is kept, which is the
difference between a suite that can detect a control API regression and one that hides it. If a
step after a control call is racy on your stack, the defect is in the backend's control API and it
belongs in that backend's issue tracker; raising a pause in four languages is not the fix.

## Running it

```bash
mvn test -Dtest=MyProviderTckTest
```

A suite extending `ProviderTckTest` with in-process control needs no Docker and no network. A suite
extending `ContainerizedProviderTckTest` needs a working Docker daemon for its Compose stack.

### Containerised suites are excluded from the default build, on purpose

**Why** an adoption suite is excluded rather than gating, and the two mistakes that exclusion
invites, are written down once for all four languages in
[Appendix F: Running the suite in CI](https://github.com/open-feature/spec/blob/main/specification/appendix-f-provider-conformance.md#running-the-suite-in-ci).
Read that first. What follows is only the Maven mechanism, which is this repository's and not the
appendix's business.

A `ContainerizedProviderTckTest` subclass is **excluded from the module's default test run**, and
the adopting module says so in its own POM. This repository's convention is the `testExclusions`
property the parent POM feeds to Surefire:

```xml
<properties>
  <testExclusions>**/e2e/*.java</testExclusions>
</properties>
```

The parent POM defines no default for it, so a module that wants the gate must declare the property
itself. It is a **Surefire** exclusion, not a compiler one: the suite still compiles against the
harness in every build, which is what keeps an adoption from rotting unnoticed.

**Then resolve the property under every profile your CI activates** — do not read the POM, which is
the mistake the appendix names first. Here, `ci.yml`'s `main` job activates `e2e` on every push, and
`providers/flagd` has an `e2e` profile for its legacy `Run*Test` suites; that profile therefore
narrows the exclusion to `**/e2e/*TckTest.java` rather than clearing it to `<testExclusions/>`, so
the legacy suites keep running and the TCK suites stay out. Both halves of the appendix's warning
happened in this repository — one adoption never declared the property, the other had a profile
putting it back — and both were found by running this, not by reading:

```bash
mvn -Pe2e -pl providers/<your-provider> help:evaluate -Dexpression=testExclusions -DforceStdout
```

The single documented command that runs a suite deliberately, per the appendix, is the one in each
adoption's own README: `-DtestExclusions=` on the command line overrides the property for one run.

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
`the connection is lost`, `the flag was modified`,
`the flag should be part of the event payload`, `the client should be in <state> state`.

The steps the TCK added:

| Step | Why it was added |
|---|---|
| `When the connection is restored` | the flagd harness only has a self-healing `lost for {int}s` form, which cannot express "assert stale, *then* reconnect" — the reconnect races the assertion. Splitting it is why `POST /restart` is optional and why this suite binds no step to it |
| `When the resolved value is remembered` / `Then the resolved details value should have changed` | the control API only requires that `/change` changes `changing-flag`'s value, not which value it changes to; asserting a delta keeps the scenario vendor-neutral |
| `Then no exception should have been thrown` | makes the "never throws" half of the error contract explicit rather than implicit in a step failure; also covers a repeated `shutdown()` and an `initialize()` after it |
| `Then the error message should be empty` | a value *and* an error message are two contradictory signals (requirement 2.3.2); asserted on every success path |
| `Then the provider metadata name should not be empty` | a conformance report keyed on the provider's name cannot be attributed if the name is empty (requirement 2.1.1) |
| `When the provider is shut down` / `When the provider is initialized again` | call the provider's own `shutdown()` and `initialize()` directly, not through the SDK — replacing the provider would test the SDK's bookkeeping, which Appendix B covers; the SDK is not told, so the next evaluation through the same client reaches the re-initialised provider |
| `Then the shutdown should have completed within {int}ms` | a shutdown that waits for a graceful close of a connection that will never answer hangs the host application's own shutdown |

## Where these artifacts come from

The feature files, the canonical flag set and the control API document are **not Java artifacts**.
They are language-agnostic definitions of the provider contract that every language's TCK must agree
on byte for byte, and that backend vendors implement in whatever language their testbed is written
in.

They live in the OpenFeature [spec repository](https://github.com/open-feature/spec) as
[Appendix F: Provider Conformance](https://github.com/open-feature/spec/blob/main/specification/appendix-f-provider-conformance.md),
under `specification/assets/provider-tck/`. This module is their Java delivery vehicle: the `spec`
git submodule is updated at `initialize`, the three directories are copied into
`src/main/resources/` at `generate-resources`, and from there they are packaged into the release
JAR. Consumers see no difference — the features stay on the classpath and need no submodule of their
own.

The three travel together by necessity: a feature file that evaluates `boolean-flag` is meaningless
without the flag definition, and a disconnect scenario is meaningless without the control endpoint
that produces the disconnect.

> **Do not edit `src/main/resources/gherkin/`, `flags/` or `openapi/`.** They are generated and
> git-ignored. Changes belong in `open-feature/spec` and arrive here by bumping the submodule.

Building this module therefore needs the submodule:

```bash
git submodule update --init tools/tck/spec
```

Maven does this itself at `initialize`, so a plain `mvn verify` works from a fresh clone; the
explicit command is only useful when working offline or inspecting the sources by hand.

## Known gaps

- **Evaluation context passthrough, beyond the targeting key.** `targeting-key-flag` resolves
  differently for a matching context, so a provider that drops the context is caught by the resolved
  value itself — that is what the `@targeting` scenarios do, and no echo operation is needed for it.
  What is still unverified is that the *whole* context arrives intact: a provider that forwards the
  targeting key and silently discards every other attribute passes. Closing that needs either an
  echo operation on the control API — something like `GET /last-evaluation` returning the request the
  backend last received — or a canonical flag whose rule keys on a custom attribute.
- **Targeting and bucketing correctness.** Out of scope by design: that is backend evaluation logic.
  `targeting-key-flag` carries the one rule in the canonical set, and it is there to prove the
  context reached the backend rather than to test how the backend evaluated it — which is why its
  rule is stated as behaviour and not as a syntax.
- **Caching.** Whether a stale provider keeps serving last-known values during an outage depends on
  whether it holds a local copy of the ruleset. The `@caching` tag is reserved; no scenarios yet,
  and so not declarable.
- **Setting and removing individual flags.** `BackendControl` exposes `prepareScenario()` and
  `changeFlag()` — reset to the canonical baseline, and mutate `changing-flag` — because those are
  what the Gherkin needs and what the control API defines. Finer-grained `setFlag(key, value)` /
  `removeFlag(key)` operations would need control API endpoints that do not exist yet, so adding
  them to the interface would produce methods `HttpBackendControl` could not implement. They belong
  to a control API revision, not to the Java seam.
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
