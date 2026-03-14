# flagd-api-testkit

A testkit for verifying implementations of the
[flagd-api](../flagd-api/README.md) `Evaluator` interface.

## What it provides

| Artifact | Description |
|---|---|
| `AbstractEvaluatorTest` | Abstract JUnit Suite — extend this and implement one method |
| `EvaluatorFactory` | SPI interface — implement `create(String flagsJson)` |
| `EvaluatorInitSteps` | Cucumber steps — `@Given("an evaluator")` and context reset (internal) |
| `EvaluatorState` | Shared Cucumber scenario state — PicoContainer DI (internal) |
| `EvaluationSteps` | `Given`/`When`/`Then` steps for flag evaluation (internal) |
| `ContextSteps` | Steps for building up evaluation context (internal) |
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

### 2. Write one class

Extend `AbstractEvaluatorTest` and implement `create()`. That's it — no Cucumber annotations,
no runner configuration, no glue packages.

```java
public class MyEvaluatorTest extends AbstractEvaluatorTest {

    @Override
    public Evaluator create(String flagsJson) throws Exception {
        MyEvaluator evaluator = new MyEvaluator();
        evaluator.setFlags(flagsJson);
        return evaluator;
    }
}
```

### 3. Register via SPI

Create the file:
```
src/test/resources/META-INF/services/dev.openfeature.contrib.tools.flagd.api.testkit.EvaluatorFactory
```
containing the fully-qualified name of your test class:
```
com.example.e2e.MyEvaluatorTest
```

That's all. Run your tests normally with Maven or your IDE.

## How it works

The `EvaluatorFactory` SPI is discovered via `java.util.ServiceLoader` inside the testkit's
`@Given("an evaluator")` step — no Cucumber glue scanning of the consumer's package is needed.
All Cucumber runner configuration (`@Suite`, `@SelectClasspathResource("features")`,
`GLUE_PROPERTY_NAME`, `OBJECT_FACTORY_PROPERTY_NAME`) is inherited from `AbstractEvaluatorTest`.

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
