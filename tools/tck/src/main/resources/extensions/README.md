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

Step classes may take `dev.openfeature.contrib.tools.tck.TckState` as a constructor argument, and
use `TckRuntime.get()` for the harness and backend control. The built-in step definitions live in
`dev.openfeature.contrib.tools.tck.internal.steps`; they are internal, not API, so only depend on
`TckState` and `TckRuntime`.

## Why `extensions/` and not `gherkin/`

A file in `gherkin/` with a canonical name would silently replace the canonical file. A separate
directory avoids that and keeps extension scenarios distinguishable from conformance results.
