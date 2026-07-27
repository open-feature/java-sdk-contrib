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

Plus one line at
`src/test/resources/META-INF/services/dev.openfeature.contrib.tools.providertck.ProviderTckHarness`:

```
com.example.MyProviderTckTest
```

That is the whole adoption. The Compose lifecycle, port discovery, control API calls, provider
registration, event awaiting and teardown all belong to the TCK. **If you find yourself adding
test infrastructure to this class, that is a bug in the TCK — please open an issue rather than
working around it.**

`createUnavailableProvider()` should point at a closed port on localhost, not at your stack — the
stack must stay up, and simulated outages belong to the control API. Give it a short connection
deadline; the scenario allows a bounded time for the error event and a 30-second connect timeout
will not make it.

#### Several provider modes

Providers with more than one transport (remote evaluation vs. in-process, say) register one harness
class per mode and select between them with a system property, typically one Surefire execution
each:

```
-Dopenfeature.tck.harness=MyProviderRpcTckTest
```

With a single registered harness the property is not needed.

## Declaring capabilities

Not every provider implements every optional part of the spec. Scenarios that exercise an optional
capability carry a tag; declare which ones you support and the rest are reported as **skipped**,
with the reason printed. They are never silently passed — a conformance suite that quietly goes
green on scenarios it did not run is worse than no suite at all.

| Capability | Tag | Meaning |
|---|---|---|
| `EVENTS` | `@events` | emits lifecycle events at all |
| `STALE` | `@stale` | enters `STALE` and emits `PROVIDER_STALE` on backend loss |
| `CONFIGURATION_CHANGE` | `@configuration-change` | detects config changes, emits `PROVIDER_CONFIGURATION_CHANGED` |
| `OBJECT` | `@object` | supports structured flag values |
| `UNAVAILABLE_INIT` | `@unavailable` | reports an error state instead of hanging on a dead backend |
| `STRICT_NUMERIC_TYPING` | `@strict-numeric-typing` | does not coerce between integer and float |
| `TARGETING` | `@targeting` | reserved, no scenarios yet |
| `CACHING` | `@caching` | reserved, no scenarios yet |

The default is every capability. **Narrow it, do not widen it**: start from the default, run the
suite, and remove only what your provider genuinely cannot do.

```java
@Override
public Set<Capability> capabilities() {
    return EnumSet.complementOf(EnumSet.of(Capability.STALE, Capability.CACHING));
}
```

A note on `STRICT_NUMERIC_TYPING`: unlike the others it is not an optional feature. The spec
requires `TYPE_MISMATCH` when the requested type cannot be satisfied, and narrowing `0.5` to `0`
loses information silently — the worst failure mode for a feature flag, because the application
sees a plausible value and no error. It is a capability only so a provider with this defect can
adopt the TCK today and see the gap reported explicitly. Not declaring it is an admission of a
known bug. **The flagd provider currently does not declare it** — see
[`FlagdTckTest`](../../providers/flagd/src/test/java/dev/openfeature/contrib/providers/flagd/e2e/FlagdTckTest.java).

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
  `@targeting` tag is reserved for context-passthrough scenarios once the gap above is closed.
- **Caching.** Whether a stale provider keeps serving last-known values during an outage depends on
  whether it holds a local copy of the ruleset. The `@caching` tag is reserved; no scenarios yet.
- **Hooks.** Not covered.
- **Flag metadata.** The flagd harness has metadata scenarios; they are not yet ported.
- **Multi-suite JVMs.** `TckRuntime` is static, so one TCK suite may run per JVM fork at a time.
- **Scenario coverage is a representative subset**, covering each architectural mechanism once
  rather than exhaustively.

## Contributing

See the repository [CONTRIBUTING.md](../../CONTRIBUTING.md). New scenarios should be portable
across providers: if a scenario can only pass against one vendor's backend semantics, it belongs in
that provider's own suite, not here.
