# OpenFeature Provider TCK (Java)

A conformance suite any OpenFeature Java provider can adopt to verify it implements the provider
contract of the [specification](https://openfeature.dev/specification/). It is the Java implementation
of [Appendix F][appendix-f], which defines the shared scenarios, canonical flag set, control API and
capability vocabulary; read it for anything true of the suite rather than this artifact (what is
tested, rules for declaring capabilities, known deviations, CI guidance). Tracking issue:
[open-feature/spec#417][tracking].

> **Status: proof of concept.** Scenarios cover each mechanism once rather than exhaustively. Expect
> breaking changes.

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

- Java 11+ and JUnit 5; Docker only for providers with an external backend.
- Testcontainers is `provided`/`optional`; containerized adopters add `org.testcontainers:testcontainers` themselves.
- The SDK is a `provided` version range (`[1.21.0,1.99999)`), never pinned.

## Quick start

| | Extend | Backend control |
| --- | --- | --- |
| External backend | `ContainerizedProviderTckTest` | built in, over the HTTP control API |
| No backend (in-memory, env vars, local file) | `ProviderTckTest` | an in-process `BackendControl` |

### A provider with a backend

Write a Compose file exposing your backend and its [control API][control-api], seed it with the
[canonical flag set][flags] (see the [assets README][assets]; several properties are easy to break
while seeding), then:

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

That's the whole adoption. The TCK owns the Compose lifecycle, port discovery, control calls, provider
registration, event awaiting and teardown, and the assets are packaged in the JAR (no submodule
needed). If you find yourself adding test infrastructure here, please open an issue.

`createUnavailableProvider()` should target a closed local port, not your stack (the stack stays up;
outages are simulated through the control API), with a short connection deadline since failure
scenarios assert promptness.

### A provider with no backend

`InProcessBackendControl` supports the SDK's `InMemoryProvider`. Copy the shape; for your own provider,
`createProvider()` returns yours and the `BackendControl` reaches its live flag store (it must reach the
live provider instance so `changeFlag()` can emit events).

```java
public class InMemoryProviderTckTest extends ProviderTckTest {

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

This path is for providers with nothing to contract with (see Appendix F, "Providers with no
backend"). In-process control cannot `disconnect()`/`reconnect()`, so withhold `STALE` and
`UNAVAILABLE_INIT`; declaring them fails with `UnsupportedOperationException` (a configuration bug,
never a skip).

### Several provider modes

Write one class per mode; nothing else is needed. Each class is its own suite with its own Compose
stack, and abstract classes are not run, so a shared intermediate base is safe. Modes may differ in
timing too (e.g. flagd in-process needs a longer init deadline than RPC).

## Options

Overridable on `ContainerizedProviderTckTest`; names and defaults match every language's TCK.

| Option | Default | Notes |
| --- | --- | --- |
| `composeFile()` | required | relative to the Maven module directory |
| `backendPorts()` | required | container ports the provider connects to; don't list the control port |
| `backendService()` | `backend` | service hosting both the backend and control API |
| `controlPort()` | `8080` | |
| `additionalPorts()` | none | `Map<String, List<Integer>>` of extra service to ports, resolved via `BackendEndpoint` by service name |
| `backendConfiguration()` | `default` | name passed to the control API's `POST /start` |
| `startupTimeout()` | 60s | stack and control API becoming reachable |
| `eventTimeout()` | 12s | waiting for a provider event; exceed your worst-case detection latency |
| `readyTimeout()` | 30s | waiting for a provider lifecycle state |

- Never pin host ports; they're mapped dynamically and discovered after startup.
- Timeouts are bounds on awaits, never pauses; nothing sleeps after a control call.
- `backendConfiguration()` names a **backend** configuration; `configuration()` names the **provider**
  mode. `configuration()` defaults to the hyphenated class name minus the JUnit suffix (`InProcessTest`
  becomes `in-process`), so set it explicitly when the class name doesn't identify the provider
  (flagd uses `flagd-rpc` and `flagd-in-process`).
- `BackendControl.controlApi()` states the control contract (`ControlApi.HTTP` or `IN_PROCESS`).

## Capabilities

Scenarios for optional behavior are tagged; undeclared capabilities are reported as **skipped with a
reason**, never passed. When to declare or withhold is [Appendix F's][appendix-f] ("Capabilities",
"Rules for declaring"); the decision is per scenario, not per tag.

| Capability | Tag | | Capability | Tag |
| --- | --- | --- | --- | --- |
| `LIFECYCLE` | `@lifecycle` | | `UNAVAILABLE_INIT` | `@unavailable` |
| `REINITIALIZATION` | `@reinitialization` | | `NUMERIC_COERCION` | `@numeric-coercion` |
| `EVENTS` | `@events` | | `TARGETING` | `@targeting` |
| `STALE` | `@stale` | | `STANDARD_REASONS` | `@standard-reasons` |
| `CONFIGURATION_CHANGE` | `@configuration-change` | | `STRING_TYPING` | `@string-typing` |
| `OBJECT` | `@object` | | `LARGE_INTEGERS` | `@large-integers` (1) |
| `VARIANTS` | `@variants` | | `CACHING` | `@caching` (2) |
| `DISABLED_FLAGS` | `@disabled-flags` | | `FULLY_TYPED_VALUES` | `@fully-typed-values` |

(1) not declarable in Java (2) reserved, not declarable

The default is every declarable capability. Narrow it, don't widen it: run the suite and remove only
what your provider genuinely can't do.

```java
@Override
public Set<Capability> capabilities() {
    return Capability.declarableExcept(Capability.STALE);
}
```

- Use `Capability.declarable()`/`declarableExcept(...)`, not `EnumSet.complementOf(...)` (which would
  include reserved and inexpressible capabilities); naming a refused capability is an error.
- `LARGE_INTEGERS` is inexpressible: `Client.getIntegerDetails` is 32-bit. Its scenario is skipped with
  a reason naming the SDK, distinct from "provider declined". Neither this nor a reserved capability
  needs a `KnownDeviation`.
- `FULLY_TYPED_VALUES` (floats and structures requested as strings) implies `STRING_TYPING` (booleans
  and integers); declare it only alongside. A store may legitimately support only the latter.

### Known deviations

A `knownDeviations()` entry means the provider fails something it's **required** to do (a numbered
`MUST`, or a rule the implementation bound itself to). If the spec permits the behavior, withhold the
capability instead.

```java
@Override
public List<KnownDeviation> knownDeviations() {
    return List.of(KnownDeviation.tracked(
            Capability.NUMERIC_COERCION,
            "https://github.com/open-feature/java-sdk-contrib/issues/1234",
            "float-flag through the integer API returns 0 with no error code"));
}
```

- `summary` is required; use `KnownDeviation.untracked(...)` without an issue.
- Capability may be `null` for an ungated scenario; `CACHING` and `LARGE_INTEGERS` are refused.
- Empty (the default) is silence, not a claim of having none.

## Running it

```bash
# once, if the module is not installed locally
mvn -pl tools/tck -am -DskipTests install

mvn -Ptck -pl providers/<your-provider> test
```

Don't add `-am` to the second command; it runs the dependencies' unit suites first and mixes their
failures into the `-Ptck` result.

Scenarios run serially (enforced; control API state is global to the stack), and the stack starts
once per suite and is never restarted.

Put the adoption in its own `tck` test package (e.g. `.../providers/flagd/tck/`, a sibling of `e2e`),
keep the `*Test` suffix for Surefire, and check `configuration()` when renaming. Exclude it from the
default build and select it with a `tck` profile:

```xml
<properties>
  <testExclusions>**/e2e/*.java,**/tck/*.java</testExclusions>
</properties>

<profile>
  <id>tck</id>
  <properties>
    <testExclusions>**/e2e/*.java</testExclusions>
  </properties>
  <build><plugins><plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration><includes><include>**/tck/*.java</include></includes></configuration>
  </plugin></plugins></build>
</profile>
```

- Both halves of the profile are needed: dropping the exclusion alone also runs unit tests; narrowing
  includes alone runs nothing.
- `testExclusions` is a Surefire (not compiler) exclusion, so the adoption still compiles in every build.
- Verify the property under every profile your CI activates:
  `mvn -Pe2e -pl providers/<your-provider> help:evaluate -Dexpression=testExclusions -DforceStdout`

## Extensions

Provider-specific scenarios (e.g. flagd's `fractional`) run inside the same suite:

```
src/test/resources/extensions/fractional.feature
src/test/java/openfeature/tck/extensions/FractionalSteps.java   // package openfeature.tck.extensions
```

- Same backend lifecycle and `BackendControl`; canonical steps are on the glue path, so an extension
  can open with `Given a stable provider`.
- Step classes may inject `TckState` and use `ProviderTck.get()` for backend control and endpoint. Don't
  build your own client; it would resolve against a provider the suite never registered.
- Don't put files under `gherkin/`; a same-named file would silently shadow a canonical one.
- Unknown tags are tolerated in `extensions/` (they gate nothing) but fail the run in `gherkin/`.
- `ProviderTck` exposes the suite's Cucumber constants (`GLUE`, `EXTENSION_GLUE`, `ALL_GLUE`, ...) for
  custom `@ConfigurationParameter`s; always keep `ProviderTck.GLUE` or canonical steps go undefined.
- Portable scenarios belong in the TCK, not in an extension.

## Java notes

- **Suite discovery** relies on the JUnit Platform auto-registering the TCK's `TestExecutionListener`
  (Surefire, Gradle and IDEs do by default). If disabled, register your harness in
  `src/test/resources/META-INF/services/dev.openfeature.contrib.tools.tck.ProviderTckHarness` and pick
  between several with `-Dopenfeature.tck.harness=RpcTest`.
- **Reference adoptions** (Docker-free, under a second): `InMemoryProviderTckTest`,
  `ControllableProviderTckTest` (real initialization; covers `@lifecycle`) and `MultiProviderTckTest`
  (can't declare `CONFIGURATION_CHANGE`; see
  [java-sdk#1882](https://github.com/open-feature/java-sdk/issues/1882)).
- **Packaged assets are generated** from the `spec` submodule (`specification/assets/provider-tck/`):
  the submodule is updated at `initialize` (skip with `-Dtck.spec.checkout.skip=true`) and copied into
  `src/main/resources/` at `generate-resources`. Don't edit `gherkin/`, `flags/` or `openapi/` there;
  they're git-ignored and changes belong in [open-feature/spec][spec]. `CanonicalAssetDigestTest`
  fails on a stale checkout, and `CanonicalTagCoverageTest` fails on a pin carrying a capability tag
  this module doesn't know.
- **Step vocabulary** is inherited from the [flagd test harness](https://github.com/open-feature/test-harness)
  where provider-neutral; only `Given a stable/unavailable flagd provider` were renamed to drop the
  vendor. Read the javadoc on the step classes before writing an extension step.

## Known gaps

- See [Appendix F][appendix-f] for suite-wide gaps (context passthrough beyond the targeting key,
  per-flag control, caching, `@stale` without containers, hooks, flag metadata).
- `ProviderTck` is static; suites in one JVM fork run sequentially, not concurrently.

## Contributing

See [CONTRIBUTING.md](../../CONTRIBUTING.md). New scenarios must be portable across providers; vendor
specific behavior belongs in that provider's own suite or an extension.

[appendix-f]: https://github.com/open-feature/spec/blob/main/specification/appendix-f-provider-conformance.md
[assets]: https://github.com/open-feature/spec/blob/main/specification/assets/provider-tck/README.md
[control-api]: https://github.com/open-feature/spec/blob/main/specification/assets/provider-tck/openapi/control-api.yaml
[flags]: https://github.com/open-feature/spec/blob/main/specification/assets/provider-tck/flags/canonical-flags.json
[spec]: https://github.com/open-feature/spec
[tracking]: https://github.com/open-feature/spec/issues/417
