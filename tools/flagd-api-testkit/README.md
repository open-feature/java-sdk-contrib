# flagd-api-testkit

A testkit for verifying implementations of the
[flagd-api](../flagd-api/README.md) `Evaluator` interface.

## What it provides

| Artifact | Description |
|---|---|
| `EvaluatorFactory` | `@FunctionalInterface` — register a lambda to create your `Evaluator` |
| `EvaluatorInitSteps` | Concrete Cucumber step class — owns `@Given("an evaluator")` and context reset |
| `EvaluatorState` | Shared Cucumber scenario state — injected via PicoContainer |
| `EvaluationSteps` | `Given`/`When`/`Then` steps for flag evaluation |
| `ContextSteps` | Steps for building up evaluation context |
| `TestkitFlags` | Loads the bundled flag configuration JSON from the classpath |
| `features/*.feature` | Gherkin scenarios bundled in the JAR |
| `flags/testkit-flags.json` | Flag configuration bundled in the JAR |

The Gherkin features and flag configuration are sourced from the
[open-feature/test-harness](https://github.com/open-feature/test-harness) repository
(`evaluator/` subdirectory) via a git submodule and are **packaged into the release JAR**
at build time. Consumers do not need a submodule of their own.

## Usage

### 1. Add the dependency (test scope)

```xml
<dependency>
    <groupId>dev.openfeature.contrib.tools</groupId>
    <artifactId>flagd-api-testkit</artifactId>
    <version><!-- latest --></version>
    <scope>test</scope>
</dependency>

<!-- Cucumber runtime deps -->
<dependency>
    <groupId>io.cucumber</groupId>
    <artifactId>cucumber-junit-platform-engine</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>io.cucumber</groupId>
    <artifactId>cucumber-picocontainer</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.junit.platform</groupId>
    <artifactId>junit-platform-suite</artifactId>
    <scope>test</scope>
</dependency>
```

### 2. Register an `EvaluatorFactory` lambda

Create a single glue class with a `@Before` hook that registers a factory lambda on `EvaluatorState`.
The `@Before` ensures Cucumber discovers the class, and the factory is set before the testkit's
`@Given("an evaluator")` step fires and invokes it with the bundled flag JSON.

```java
package com.example.e2e;

import dev.openfeature.contrib.tools.flagd.api.testkit.EvaluatorState;
import io.cucumber.java.Before;

public class MyEvaluatorSetup {

    private final EvaluatorState state;

    public MyEvaluatorSetup(EvaluatorState state) {
        this.state = state;
    }

    @Before
    public void registerFactory() {
        state.setFactory(flagsJson -> {
            MyEvaluator evaluator = new MyEvaluator();
            evaluator.setFlags(flagsJson);
            return evaluator;
        });
    }
}
```

### 3. Create a Cucumber test runner

```java
package com.example.e2e;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.OBJECT_FACTORY_PROPERTY_NAME;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")            // discovers features from the testkit JAR
@ConfigurationParameter(
    key = GLUE_PROPERTY_NAME,
    value = "dev.openfeature.contrib.tools.flagd.api.testkit,"  // testkit steps
          + "com.example.e2e")                                   // your init step
@ConfigurationParameter(
    key = OBJECT_FACTORY_PROPERTY_NAME,
    value = "io.cucumber.picocontainer.PicoFactory")
public class EvaluatorComplianceTest {}
```

## Building / submodule setup

The feature files and flags are **not committed** to this repository — they are
sourced from the [open-feature/test-harness](https://github.com/open-feature/test-harness)
submodule. After cloning this repo:

```bash
# from tools/flagd-api-testkit/
git submodule update --init test-harness
```

The Maven build then copies `test-harness/evaluator/gherkin/` and
`test-harness/evaluator/flags/` into `src/main/resources/` automatically during
`generate-resources`, so they are included in the JAR.
