# Provider TCK extension point

Feature files on the classpath under `extensions/` run in the TCK suite alongside the canonical
scenarios. This README keeps the directory present on the classpath; Cucumber ignores non-`.feature`
files.

## Adding scenarios

```
src/test/resources/extensions/fractional.feature
src/test/java/openfeature/tck/extensions/FractionalSteps.java   // package openfeature.tck.extensions
```

Steps in the `openfeature.tck.extensions` glue package are picked up automatically and share the
suite's backend lifecycle.

Step classes declare what they need as constructor arguments -- any of `TckState`,
`ProviderTckHarness` and `BackendControl`:

```java
public FractionalSteps(TckState state, ProviderTckHarness harness, BackendControl backend) { ... }
```

The built-in step definitions live in `dev.openfeature.contrib.tools.tck.internal.steps`; they are
internal, not API.

Both the step class and each step method must be `public` -- `PicoFactory` registers only public,
non-abstract classes and `MethodScanner` uses `Class.getMethods()`. Neither complains when it
finds nothing: the scenario reports *undefined* and a non-strict run reads as a pass.

## Why `extensions/` and not `gherkin/`

A file in `gherkin/` with a canonical name would silently replace the canonical file. A separate
directory avoids that and keeps extension scenarios distinguishable from conformance results.
