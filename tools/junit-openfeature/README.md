# JUnit Open Feature extension

A JUnit5 extension to reduce boilerplate code for testing code which utilizes OpenFeature.

## Installation
<!-- x-release-please-start-version -->
```xml
<dependency>
  <groupId>dev.openfeature.contrib.tools</groupId>
  <artifactId>junitopenfeature</artifactId>
  <version>0.2.0</version>
  <scope>test</scope>
</dependency>
```
<!-- x-release-please-end-version -->

## Getting Started

- We are supporting two different flavors for testing, a [simple](#simple-configuration) and an [extended](#extended-configuration) configuration.
- Both the [`@Test`](https://junit.org/junit5/docs/5.9.0/api/org.junit.jupiter.api/org/junit/jupiter/api/Test.html) annotation and the [`@ParameterizedTest`](https://junit.org/junit5/docs/5.9.0/api/org.junit.jupiter.params/org/junit/jupiter/params/ParameterizedTest.html) annotation are supported.
      
Notice: We are most likely not multithread compatible!
### Simple Configuration

Choose the simple configuration if you are only testing in one domain.
Per default, it will be used in the global domain.

```java
@Test
@Flag(name = "BOOLEAN_FLAG", value = "true")
void test() {
    // your test code
}
``` 
 
#### Multiple flags

The `@Flag` annotation can be also repeated multiple times.

```java
@Test
@Flag(name = "BOOLEAN_FLAG", value = "true")
@Flag(name = "BOOLEAN_FLAG2", value = "true")
void test() {
    // your test code
}
``` 

#### Defining Flags for a whole test-class

`@Flags` can be defined on the class-level too, but method-level
annotations will supersede class-level annotations.

```java
@Flag(name = "BOOLEAN_FLAG", value = "true")
@Flag(name = "BOOLEAN_FLAG2", value = "false")
class Test {
    @Test
    @Flag(name = "BOOLEAN_FLAG2", value = "true") // will be used
    void test() {
        // your test code
    }
}
``` 

#### Setting a different domain

You can define your own domain on the test-class-level with `@OpenFeatureDefaultDomain` like:

```java
@OpenFeatureDefaultDomain("domain")
class Test {
    @Test
    @Flag(name = "BOOLEAN_FLAG", value = "true")
        // this flag will be available in the `domain` domain
    void test() {
        // your test code
    }
}
```

### Extended Configuration

Use the extended configuration when your code needs to use multiple domains.

```java
@Test
@OpenFeature({
        @Flag(name = "BOOLEAN_FLAG", value = "true")
})
@OpenFeature(
        domain = "domain",
        value = {
            @Flag(name = "BOOLEAN_FLAG2", value = "true")
        })
void test() {
    // your test code
}
``` 


#### Multiple flags

The `@Flag` annotation can be also repeated multiple times.

```java
@Test
@OpenFeature({
        @Flag(name = "BOOLEAN_FLAG", value = "true"),
        @Flag(name = "BOOLEAN_FLAG2", value = "true")
})
void test() {
    // your test code
}
``` 

#### Defining Flags for a whole test-class

`@Flag` can be defined on the class-level too, but method-level
annotations will superseded class-level annotations.

```java
@OpenFeature({
        @Flag(name = "BOOLEAN_FLAG", value = "true"),
        @Flag(name = "BOOLEAN_FLAG2", value = "false")
})
class Test {
    @Test
    @OpenFeature({
            @Flag(name = "BOOLEAN_FLAG2", value = "true") // will be used
    })
    void test() {
        // your test code
    }
}
``` 

#### Setting a different domain

You can define an own domain for each usage of the `@OpenFeature` annotation with the `domain` property:

```java
@Test
@OpenFeature(
    domain = "domain",
    value = {
        @Flag(name = "BOOLEAN_FLAG2", value = "true") // will be used
})
    // this flag will be available in the `domain` domain
void test() {
    // your test code
}
```

