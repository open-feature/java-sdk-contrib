# Provider TCK extension point

Feature files placed on the classpath under `tck-extensions/` run inside the TCK suite, alongside
the canonical conformance scenarios.

This file is here so that the directory exists on the classpath even when nobody has extended
anything. `AbstractProviderTckTest` selects `tck-extensions` unconditionally, and a classpath
resource selector naming a resource that exists on no classpath root is a discovery error rather
than an empty selection. Cucumber ignores files that are not `.feature`, so the README itself is
never read as a scenario.

## Adding scenarios

Write nothing but the two files:

```
src/test/resources/tck-extensions/fractional.feature
src/test/java/openfeature/tck/extensions/FractionalSteps.java   // package openfeature.tck.extensions
```

No annotations, no second suite, no runner configuration. The scenarios are discovered by the same
suite as the canonical set, so they share its backend lifecycle: one Compose stack, one
`@BeforeAll`, the same control API, the same conformance report.

Your step classes may take `dev.openfeature.contrib.tools.providertck.TckState` as a constructor
argument to reach the client and the last evaluation, exactly as the canonical steps do, and
`TckRuntime.get()` for the control API and the backend endpoint.

## Why this directory rather than `features/`

Two classpath roots containing the same directory are scanned additively. Two containing the same
directory *and* the same file name are not: one silently wins. A feature file added to `features/`
under a canonical name would therefore replace a canonical file, and the suite would report success
having run the replacement. The extension directory has a different name so that collision cannot
be reached by accident.

`features/` is the canonical set and belongs to the specification. Extensions are yours.

## What extensions are not

An extension scenario is not conformance. It does not appear in the canonical set, it cannot make
the canonical set smaller, and a conformance report is not a claim about it. If a scenario is
portable across providers it belongs in `features/` — send it to the TCK.
