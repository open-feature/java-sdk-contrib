# Contributing

## System Requirements

Java 11+ is required for the tooling, plugins, etc. Maven 3.8+ is recommended.

## Compilation target(s)

As in the Java-SDK, we target Java 11. The parent POM configures this automatically.

## Adding a module

1. Create a [standard directory structure](https://maven.apache.org/guides/introduction/introduction-to-the-standard-directory-layout.html) in the appropriate folder (`hooks/`, `providers/`).
1. Create a new `pom.xml` in the root of your new module. It must inherit from the parent POM, which implements the javadoc, testing, publishing, and other boilerplate. Be sure to add `<!--x-release-please-version -->` on the line specifying the module version, so our release tooling can update it (see sample pom below).
1. Add the new package to `release-please-config.json`.
1. Add the new module to the `<modules>...</modules>` section in the parent `pom.xml`.
1. Add a `version.txt` file with a version matching that in your new `pom.xml`, e.g. `0.0.1`.
1. If you care to release a pre 1.0.0 version, add the same version above to `.release-please-manifest.json`. Failing to do this will release a `1.0.0` initial release.

Sample pom.xml:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
	<modelVersion>4.0.0</modelVersion>
	<modelVersion>4.0.0</modelVersion>
	<parent>
		<groupId>dev.openfeature.contrib</groupId>
		<artifactId>parent</artifactId>		
		<version><!-- current parent pom version --></version>
		<relativePath>../../pom.xml</relativePath>
	</parent>
	<!-- The group id MUST start with dev.openfeature, or publishing will fail. OpenFeature has verified ownership of this (reversed) domain. -->
	<groupId>dev.openfeature.contrib.${providers | hooks | etc}</groupId>
	<artifactId>module</artifactId>
	<version>0.0.1</version> <!--x-release-please-version -->

	<name>module</name>
	<description>Your module description</description>
	<url>https://openfeature.dev</url>

	<developers>
		<developer>
			<id>Your GitHub ID</id>
			<name>Your Name</name>
			<organization>OpenFeature</organization>
			<url>https://openfeature.dev/</url>
		</developer>
	</developers>

  <dependencies>
    <!-- dependencies your module needs (in addition to those inherited from parent) -->
  </dependencies>
	
  <build>
    <plugins>
      <!-- plugins your module needs (in addition to those inherited from parent) -->
    </plugins>
  </build>

</project>
```

## Documentation

Any published modules must have documentation in their root directory, explaining the basic purpose of the module as well as installation and usage instructions.
Instructions for how to develop a module should also be included (required system dependencies, instructions for testing locally, etc).

## Code Styles

### Overview
Our project follows strict code formatting standards to maintain consistency and readability across the codebase. We use [Spotless](https://github.com/diffplug/spotless) integrated with the [Palantir Java Format](https://github.com/palantir/palantir-java-format) for code formatting.

**Spotless** ensures that all code complies with the formatting rules automatically, reducing style-related issues during code reviews.

### How to Format Your Code
1. **Before Committing Changes:**
   Run the Spotless plugin to format your code. This will apply the Palantir Java Format style:
   ```bash
   mvn spotless:apply
   ```

2. **Verify Formatting:**
   To check if your code adheres to the style guidelines without making changes:
   ```bash
   mvn spotless:check
   ```

    - If this command fails, your code does not follow the required formatting. Use `mvn spotless:apply` to fix it.

### CI/CD Integration
Our Continuous Integration (CI) pipeline automatically checks code formatting using the Spotless plugin. Any code that does not pass the `spotless:check` step will cause the build to fail.

### Best Practices
- Regularly run `mvn spotless:apply` during your work to ensure your code remains aligned with the standards.
- Configure your IDE (e.g., IntelliJ IDEA or Eclipse) to follow the Palantir Java format guidelines to reduce discrepancies during development.

### Support
If you encounter issues with code formatting, please raise a GitHub issue or contact the maintainers.

## Testing

Any published modules must have reasonable test coverage.
The parent POM adds basic testing support for you.

> [!TIP]
> For easier usage maven wrapper is available. Example usage: `./mvnw` (unix) or `./mvnw.cmd` (win)

Use `mvn clean test` to test the entire project.
Use `mvn --projects {MODULE PATH} clean test` to test just a single module.

Use `mvn clean verify` to test/audit/lint the entire project.
Use `mvn --projects {MODULE PATH} clean verify` to test/audit/lint just a single module.

## Versioning and releasing

As described in the [README](./README.md), this project uses release-please, and semantic versioning.
Breaking changes should be identified by using a semantic PR title.

## Dependencies

Keep dependencies to a minimum, especially `compile` dependencies. Do not include dependencies specific to your module in the parent, add them to your module's `POM.xml`.
The Java-SDK should be a _provided_ dependency of your module, the parent POM takes care of this automatically.
Keep in mind, one version of the Java-SDK is used for all modules.

## VS Code config

To use vscode, install the standard [Java language support extension by Red Hat](https://marketplace.visualstudio.com/items?itemName=redhat.java).

The following vscode settings are recommended (create a workspace settings file at .vscode/settings.json):

```json
{
  "java.configuration.updateBuildConfiguration": "interactive",
  "java.autobuild.enabled": false,
  "java.checkstyle.configuration": "${workspaceFolder}/checkstyle.xml",
  "java.checkstyle.version": "10.3.2",
  "java.format.settings.url": "${workspaceFolder}/eclipse-java-google-style.xml",
  "java.format.enabled": false
}
```
