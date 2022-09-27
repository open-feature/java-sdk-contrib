# OpenFeature Java Contributions

![Experimental](https://img.shields.io/badge/experimental-breaking%20changes%20allowed-yellow)
![Alpha](https://img.shields.io/badge/alpha-release-red)

This repository is intended for OpenFeature contributions which are not included in the [OpenFeature SDK](https://github.com/open-feature/java-sdk).

The project includes:

- [Providers](./providers)
- [Hooks](./hooks)

## Releases

This repo uses _Release Please_ to release packages. Release Please sets up a running PR that tracks all changes for the library components, and maintains the versions according to [conventional commits](https://www.conventionalcommits.org/en/v1.0.0/), generated when [PRs are merged](https://github.com/amannn/action-semantic-pull-request). When Release Please's running PR is merged, any changed artifacts are published.

## Developing

### Requirements

Though we target Java 8, Java 18 is recommended for the tooling, plugins, etc. Maven 3.8+ is recommended.

### Testing

Run `mvn verify` to test, generate javadoc, and check style. If this passes locally, the CI will generally pass.

### Adding a module

1. Create a [standard directory structure](https://maven.apache.org/guides/introduction/introduction-to-the-standard-directory-layout.html) in the appropriate folder (`hooks/`, `providers/`).
1. Create a new `pom.xml` in the root of your new module. It must inherit from the parent POM, which implements the javadoc, testing, publishing, and other boilerplate. Be sure to add `<!--x-release-please-version -->` on the line specifying the module version, so our release tooling can update it (see sample pom below).
1. Add the new package to `release-please-config.json`.
1. Add the new module to the `<modules>...</modules>` section in the parent `pom.xml`.
1. Add a `version.txt` file with a version matching that in your new `pom.xml`, e.g. `0.0.1`.
2. If you care to release a pre 1.0.0 version, add the same version above to `.release-please-manifest.json`. Failing to do this will release a `1.0.0` initial release.

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

### VS Code config

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

## License

Apache 2.0 - See [LICENSE](./LICENSE) for more information.
