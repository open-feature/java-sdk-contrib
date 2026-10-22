# Pooled Compose stacks for the Provider TCK — measurement plan

A spike to find out whether leasing Compose stacks from a pool makes the flagd TCK
meaningfully faster, and to separate that question from the two other levers it is easy to
confuse it with. Measurement first: the design below exists to produce numbers, not to be
merged as it stands.

## Baseline, measured today (same machine, same reactor shape)

| | suites | tests | suite time | flagd module | non-test overhead |
|---|---|---|---|---|---|
| legacy e2e (`-P e2e`, container pool) | 3 | 923 | 355.7s | **9:17** | 201s (36%) |
| TCK + flagd extensions (`-P tck`) | 2 | 706 | 433.3s | **9:42** | 149s (26%) |

Per-suite, TCK: `RpcTest` 164.2s, `InProcessTest` 269.1s — the in-process half is 62% of
suite time on its own.

**Variance is the headline risk.** `RpcTest` alone measured 161s, 199s, 207s and 271s today
on identical code. Anything under ~30% is noise at n=1. CI runs ~40% faster than this
machine (Docker on WSL2 on Windows), so orderings may differ there.

## Three levers, deliberately measured apart

The pool is only one of them, and on today's numbers it is the smallest.

**L1 — concurrency across suites.** `RpcTest` and `InProcessTest` run sequentially. Running
them as two surefire forks predicts 433s → ~269s of suite time, **~165s saved**, with no
pool and no new TCK machinery.

**L2 — pooling.** K warm stacks, leased per scenario, shared across suites. Saving today ≈
(suites − 1) × stack startup ≈ **one** startup, 10–30s. The saving grows with suite count,
which is why it is really an enabler for L3 rather than a win on its own.

**L3 — per-file suites.** 17 feature files (6 canonical + 11 flagd extension) as 17 suites
buys isolation and reproducibility ("run just this file"), but 17 stack startups without a
pool. With a pool of K it costs K startups. This is where L2 pays.

L2 and L1 are **alternatives, not complements**: a pool is in-process, so it cannot be
shared across surefire forks. Pool wants many suites as threads in one JVM; forking wants
separate JVMs each owning a stack.

## Design sketch (what the spike would build for L2)

### The lease point already exists

`TckObjectFactory` is the right boundary and needs no new hooks:

- `start()` / `stop()` are called **per scenario** by Cucumber — the granularity the legacy
  pool leases at.
- It is `ThreadLocal` under parallel execution, so concurrent leases are bounded by worker
  count for free.
- It already binds `BackendControl` into the pico container per scenario, so steps receive
  the control by injection and cannot tell which stack it points at.

What changes: ownership moves from factory-lifetime to scenario-lifetime. Today

```java
if (lifecycle == null) {                             // once per thread, stack survives
    lifecycle = TckLifecycle.startForExecutingSuite().orElse(null);
}
```

becomes acquire-in-`start()` / release-in-`stop()`. **Until that line changes, pooling gains
nothing.**

### Backpressure for free

`BlockingQueue.take()` in `start()` means a scenario waits for a stack rather than starting
one. Pool size then becomes the real cap on container concurrency, independent of Cucumber's
thread count — which matters because `cucumber.execution.parallel.config.fixed.parallelism`
is a target, not a cap: blocking steps grew the pool to 18 threads and 18 stacks when
uncapped. With K stacks, 18 threads simply queue. Expect alarming thread counts in logs;
`fixed.max-pool-size` stays useful for thread hygiene, no longer for container count.

### Pool size: method + property, never an annotation

- `poolSize()` overridable on the test class — the adopter knows how heavy their stack is
  (matches the existing idiom of `composeFile()`, `backendPorts()`, `controlPort()`,
  `startupTimeout()`).
- System property override — the operator knows how heavy their machine is. The legacy pool
  already does `Integer.getInteger(...)`.
- **Not `@ConfigurationParameter`**: a discovery-request parameter outranks `-D` and
  `junit-platform.properties`, so pinning it there makes it unoverridable by whoever runs
  the suite. We removed exactly such an annotation for the parallelism setting earlier.

**Default matters more than the class count.** Today's model is not "a pool of one" — it is
"K equals the worker count, implicitly". Defaulting `poolSize()` to 1 would silently
serialise every adopter running parallel today; defaulting it to the worker count preserves
behaviour while making it tunable.

### Cleaning, quarantine, keying

- **Clean on acquire, not before release.** `prepareScenario()` already runs per scenario
  after the control is bound, and a scenario that crashed or hung cannot be trusted to clean
  up on its way out. Cost: a `/reset` (or `/start?config=` when a configuration was served)
  on every scenario's critical path.
- **Quarantine on failed preparation.** A stack whose `prepareScenario` fails must not go
  back on the queue, or the pool degenerates into K broken stacks. Needs a replace-or-shrink
  policy with a loud warning. Today's `startFailure` logic poisons *the thread*, which
  assumes a thread owns its stack — that has to change.
- **Key the pool by stack description, not by suite.** `RpcTest` and `InProcessTest` use the
  same testbed compose file and launchpad, and sharing stacks across them is most of the
  win. If two suites declare different `poolSize()` for the same key, fail loudly rather
  than resolving silently — same spirit as refusing a tag that is both included and
  excluded.

### Class shape

Avoid `PooledContainerizedProviderTckTest extends ContainerizedProviderTckTest`: the parent
fixes what the child varies (`startSuite()`/`stopSuite()` are `final`, and it holds a
private `ComposeContainer`). Extract the *description* of a stack — compose file, backend
ports, additional ports, service name, control port, startup timeout, configuration (eight
methods today) — into a spec, and let two bases consume it: one owns a stack, one leases
from a pool.

### Known API gaps

- `BackendEndpoint`'s constructor is package-private and takes a `ComposeContainer`, so a
  pooled stack cannot build one. flagd's extension steps need it (`ConnectionSteps` resolves
  envoy host and ports through `flagd.backendEndpoint()`). Needs a public factory.
- Stack identity currently lives in the harness: `backendControl()` and
  `createProvider(BackendEndpoint)` read from the suite's own container. Pooled, both become
  per-lease, so the harness gains an implicit "there is a current lease" contract its
  interface does not express. **This is the part I expect to be contentious in review.**

## Spike shape

A draft PR stacked on #1951 (`feat/provider-tck-flagd-extensions`), flagd-only,
measurement-first, revert-friendly:

- branch `perf/tck-stack-pool` off #1951
- no other binding touched; no canonical asset touched
- the pool reuses the legacy `ContainerPool` (116 lines, already in the same module's test
  sources) rather than writing a second one
- each configuration below behind a flag, so one branch produces all the numbers

## Measurement protocol

Per configuration: **n = 3**, same machine, nothing else running, images pre-pulled, `clean`
each run, and `-pl tools/tck,providers/flagd` so Maven never resolves `tools/tck` from a
stale installed jar (that trap cost a run today).

Record per run: per-suite surefire time, flagd module time, reactor total, number of Compose
stack starts (count the control's startup lines), peak concurrent containers, peak host
memory.

| id | configuration | question |
|---|---|---|
| A | today | baseline, re-measured for variance |
| B | fork per suite | how much is L1 worth alone? |
| C | pool, per-scenario lease, K = 1 / 2 / 4 | how much is L2 worth, and where does K saturate? |
| D | fork + pool | confirm they conflict; document rather than fix |
| E | per-file suites + pool K = 4 | does L3 become affordable? |

**Acceptance gate, every configuration:** identical conformance outcome — 353 tests per
suite, the same four known deviations, same skip counts. A timing win that changes results
is not a win. (A pooled dirty stack would show up exactly here, which is why this gate
matters more than the timings.)

## What would make us ship what

- B ≥ 30% and C adds < 10% → ship B, park the pool until per-file granularity is wanted.
- C saturates at small K with a large win → pool is worth the harness contract change.
- E is the only configuration that beats B materially → the pool ships as part of per-file,
  not on its own.
- Nothing beats noise → keep today's design and record the measurement so the question stops
  recurring.

## Effort

- **B**: configuration only, hours.
- **C**: ~20 lines in `TckObjectFactory` + reuse of the existing pool + spec extraction + a
  `BackendEndpoint` factory. The code is a day; the *policy* (quarantine, keying, size
  conflict, the per-lease harness contract) is the real cost and the real review surface.
- **E**: adds per-file suite generation on top of C.

## Open questions for you

1. Default `poolSize()` — worker count (preserves today) or 1 (safe, but silently serialises
   parallel adopters)?
2. Is this a TCK feature or flagd-local first? Configurable pool size at TCK level implies
   the former, which contradicts "not in general, just flagd".
3. Is the per-lease harness contract acceptable, or does stack identity belong somewhere
   else entirely?
4. Pool or forks, given they are mutually exclusive — and does that answer change if the
   conformance/extension split happens first?
