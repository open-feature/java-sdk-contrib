# Changelog

## [1.1.1](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.go-feature-flag-v1.1.0...dev.openfeature.contrib.providers.go-feature-flag-v1.1.1) (2026-01-15)


### 🐛 Bug Fixes

* **gofeatureflag:** fix unreachable code when flag not found ([#1679](https://github.com/open-feature/java-sdk-contrib/issues/1679)) ([95e149e](https://github.com/open-feature/java-sdk-contrib/commit/95e149e94dc7b4478bb8022b7c74d24c71f6d9e3))

## [1.1.0](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.go-feature-flag-v1.0.1...dev.openfeature.contrib.providers.go-feature-flag-v1.1.0) (2025-11-24)


### 🐛 Bug Fixes

* **flagd:** fix wrong environment variable and test execution ([#1589](https://github.com/open-feature/java-sdk-contrib/issues/1589)) ([e1d8e54](https://github.com/open-feature/java-sdk-contrib/commit/e1d8e54cb15f7e9f27626c60cebded3690a84698))


### ✨ New Features

* compile go-feature-flag to Java bytecode ([#1628](https://github.com/open-feature/java-sdk-contrib/issues/1628)) ([129c9bd](https://github.com/open-feature/java-sdk-contrib/commit/129c9bdd130b3a193cf641c7ab02b43b2567b9df))

## [1.0.1](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.go-feature-flag-v1.0.0...dev.openfeature.contrib.providers.go-feature-flag-v1.0.1) (2025-09-19)


### 🐛 Bug Fixes

* **go-feature-flag:** fix loading wasm files in a jar mode. ([#1595](https://github.com/open-feature/java-sdk-contrib/issues/1595)) ([40f77f3](https://github.com/open-feature/java-sdk-contrib/commit/40f77f359460743a0979b7120106d936a62af279))

## [1.0.0](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.go-feature-flag-v1.0.0...dev.openfeature.contrib.providers.go-feature-flag-v1.0.0) (2025-09-19)


### ⚠ BREAKING CHANGES

* **go-feature-flag:** Introduce in-process evaluation + tracking ([#1384](https://github.com/open-feature/java-sdk-contrib/issues/1384))
* changing cache provider to caffeine over guava ([#1065](https://github.com/open-feature/java-sdk-contrib/issues/1065))

### 🐛 Bug Fixes

* **deps:** update dependency com.dylibso.chicory:runtime to v1.4.1 ([#1408](https://github.com/open-feature/java-sdk-contrib/issues/1408)) ([352ddd8](https://github.com/open-feature/java-sdk-contrib/commit/352ddd861694459c155b21a60e2938d6a2bc97bc))
* **deps:** update dependency com.dylibso.chicory:runtime to v1.5.0 ([#1462](https://github.com/open-feature/java-sdk-contrib/issues/1462)) ([93115a7](https://github.com/open-feature/java-sdk-contrib/commit/93115a7a818568109b32205535dc58211d36c151))
* **deps:** update dependency com.dylibso.chicory:wasi to v1.4.1 ([#1409](https://github.com/open-feature/java-sdk-contrib/issues/1409)) ([2bc16a8](https://github.com/open-feature/java-sdk-contrib/commit/2bc16a86b54dd8a43c1af1d4e8884d9716282f8f))
* **deps:** update dependency com.dylibso.chicory:wasi to v1.5.0 ([#1473](https://github.com/open-feature/java-sdk-contrib/issues/1473)) ([0030528](https://github.com/open-feature/java-sdk-contrib/commit/00305287986248262036dcb7cd4f4c384fe50052))
* **deps:** update dependency com.fasterxml.jackson.core:jackson-databind to v2.18.1 ([#1047](https://github.com/open-feature/java-sdk-contrib/issues/1047)) ([a2ee3e6](https://github.com/open-feature/java-sdk-contrib/commit/a2ee3e6ed0c15c3ebaf55adc10198760f51a4a30))
* **deps:** update dependency com.fasterxml.jackson.core:jackson-databind to v2.18.2 ([#1089](https://github.com/open-feature/java-sdk-contrib/issues/1089)) ([9b40e22](https://github.com/open-feature/java-sdk-contrib/commit/9b40e22e57739c7da417f834dd4f6822e6657ca8))
* **deps:** update dependency com.fasterxml.jackson.datatype:jackson-datatype-jsr310 to v2.18.1 ([#1048](https://github.com/open-feature/java-sdk-contrib/issues/1048)) ([ac1a952](https://github.com/open-feature/java-sdk-contrib/commit/ac1a95239229def83a253515b4c88ee8b9186cf1))
* **deps:** update dependency com.fasterxml.jackson.datatype:jackson-datatype-jsr310 to v2.18.2 ([#1088](https://github.com/open-feature/java-sdk-contrib/issues/1088)) ([34ec5a8](https://github.com/open-feature/java-sdk-contrib/commit/34ec5a83685100ac014def681286c59f0f939188))
* **deps:** update dependency com.fasterxml.jackson.datatype:jackson-datatype-jsr310 to v2.18.3 ([#1253](https://github.com/open-feature/java-sdk-contrib/issues/1253)) ([9cc36dc](https://github.com/open-feature/java-sdk-contrib/commit/9cc36dc8d750cf38b55b12ea22f4c7bd56a206b3))
* **deps:** update dependency com.fasterxml.jackson.datatype:jackson-datatype-jsr310 to v2.19.0 ([#1366](https://github.com/open-feature/java-sdk-contrib/issues/1366)) ([8896f50](https://github.com/open-feature/java-sdk-contrib/commit/8896f505b7e682c5954de465383b5e871cb1a8d0))
* **deps:** update dependency com.fasterxml.jackson.datatype:jackson-datatype-jsr310 to v2.19.1 ([#1415](https://github.com/open-feature/java-sdk-contrib/issues/1415)) ([c038b8e](https://github.com/open-feature/java-sdk-contrib/commit/c038b8e8f897cb3524aa9053c9167368175c6680))
* **deps:** update dependency io.reactivex.rxjava3:rxjava to v3.1.10 ([#1083](https://github.com/open-feature/java-sdk-contrib/issues/1083)) ([67d6308](https://github.com/open-feature/java-sdk-contrib/commit/67d63081c8b5f56ce7ac32a30f2bc1e67b6afe5d))
* **deps:** update jackson monorepo to v2.18.3 ([#1254](https://github.com/open-feature/java-sdk-contrib/issues/1254)) ([24f11fd](https://github.com/open-feature/java-sdk-contrib/commit/24f11fd5338e2693d1eda0654c4ea4bc29063742))
* **deps:** update jackson monorepo to v2.19.0 ([#1346](https://github.com/open-feature/java-sdk-contrib/issues/1346)) ([d4af23b](https://github.com/open-feature/java-sdk-contrib/commit/d4af23b9f2700233ea22f9132df00637820da10c))
* **deps:** update jackson monorepo to v2.19.1 ([#1416](https://github.com/open-feature/java-sdk-contrib/issues/1416)) ([43630e5](https://github.com/open-feature/java-sdk-contrib/commit/43630e5da8fd1f7f8b8ccfcb2aa77cab51949be7))
* **deps:** update slf4j monorepo to v2.0.17 ([#1243](https://github.com/open-feature/java-sdk-contrib/issues/1243)) ([66c6a7f](https://github.com/open-feature/java-sdk-contrib/commit/66c6a7fc1bdc3e907793d2fc1eb0d412693a4aee))
* **gofeatureflag:** fix etag in tests to be around quotes ([#1432](https://github.com/open-feature/java-sdk-contrib/issues/1432)) ([80084df](https://github.com/open-feature/java-sdk-contrib/commit/80084dfa6089799e828a85774be50e5b34a4987b))
* **goff:** Bump version of chicory to fix flaky tests ([#1423](https://github.com/open-feature/java-sdk-contrib/issues/1423)) ([f6996dc](https://github.com/open-feature/java-sdk-contrib/commit/f6996dcf18acb367f935569f28fc59adcb650b6f))
* **goff:** remove flacky test ([#1427](https://github.com/open-feature/java-sdk-contrib/issues/1427)) ([e455cc3](https://github.com/open-feature/java-sdk-contrib/commit/e455cc3f022223a0a09d4b9afece65bf55bdfa5f))
* require latest SDK ([#1488](https://github.com/open-feature/java-sdk-contrib/issues/1488)) ([71afb81](https://github.com/open-feature/java-sdk-contrib/commit/71afb81703bc2a5350ab967e478c527469fdb5d2))
* Use seconds instead of milliseconds ([#1174](https://github.com/open-feature/java-sdk-contrib/issues/1174)) ([921231a](https://github.com/open-feature/java-sdk-contrib/commit/921231a2031098fb97bf96e506912e2dee2b225c))


### ✨ New Features

* changing cache provider to caffeine over guava ([#1065](https://github.com/open-feature/java-sdk-contrib/issues/1065)) ([7083586](https://github.com/open-feature/java-sdk-contrib/commit/70835860090175209b6cec1cab6443d0bc4784fa))
* **go-feature-flag:** Add exporterMetadata in the evaluation call ([#1193](https://github.com/open-feature/java-sdk-contrib/issues/1193)) ([16a8287](https://github.com/open-feature/java-sdk-contrib/commit/16a8287d8ac4e952f4bb934bd731e4301ce3c261))
* **go-feature-flag:** Introduce in-process evaluation + tracking ([#1384](https://github.com/open-feature/java-sdk-contrib/issues/1384)) ([2ea5e68](https://github.com/open-feature/java-sdk-contrib/commit/2ea5e68e79c6c703c55cc815d223bbe66a60ea6d))
* **go-feature-flag:** Support Exporter Metadata ([#1167](https://github.com/open-feature/java-sdk-contrib/issues/1167)) ([b55a52e](https://github.com/open-feature/java-sdk-contrib/commit/b55a52ecd90b53d25ea259e16fa0ef43fac9dc12))
* improve error handling ([#1214](https://github.com/open-feature/java-sdk-contrib/issues/1214)) ([fd32898](https://github.com/open-feature/java-sdk-contrib/commit/fd32898c952926e5e484ea2305220b1f532d23ea))
* migrate to Java 11 ([#1336](https://github.com/open-feature/java-sdk-contrib/issues/1336)) ([a4be1ff](https://github.com/open-feature/java-sdk-contrib/commit/a4be1ff66870a72189873171e83c5b65dbb9991c))

## [0.4.3](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.go-feature-flag-v0.4.2...dev.openfeature.contrib.providers.go-feature-flag-v0.4.3) (2025-03-17)


### 🐛 Bug Fixes

* **deps:** update dependency com.fasterxml.jackson.datatype:jackson-datatype-jsr310 to v2.18.3 ([#1253](https://github.com/open-feature/java-sdk-contrib/issues/1253)) ([9cc36dc](https://github.com/open-feature/java-sdk-contrib/commit/9cc36dc8d750cf38b55b12ea22f4c7bd56a206b3))
* **deps:** update jackson monorepo to v2.18.3 ([#1254](https://github.com/open-feature/java-sdk-contrib/issues/1254)) ([24f11fd](https://github.com/open-feature/java-sdk-contrib/commit/24f11fd5338e2693d1eda0654c4ea4bc29063742))
* **deps:** update slf4j monorepo to v2.0.17 ([#1243](https://github.com/open-feature/java-sdk-contrib/issues/1243)) ([66c6a7f](https://github.com/open-feature/java-sdk-contrib/commit/66c6a7fc1bdc3e907793d2fc1eb0d412693a4aee))


### ✨ New Features

* improve error handling ([#1214](https://github.com/open-feature/java-sdk-contrib/issues/1214)) ([fd32898](https://github.com/open-feature/java-sdk-contrib/commit/fd32898c952926e5e484ea2305220b1f532d23ea))

## [0.4.2](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.go-feature-flag-v0.4.1...dev.openfeature.contrib.providers.go-feature-flag-v0.4.2) (2025-02-10)


### 🐛 Bug Fixes

* Use seconds instead of milliseconds ([#1174](https://github.com/open-feature/java-sdk-contrib/issues/1174)) ([921231a](https://github.com/open-feature/java-sdk-contrib/commit/921231a2031098fb97bf96e506912e2dee2b225c))


### ✨ New Features

* **go-feature-flag:** Add exporterMetadata in the evaluation call ([#1193](https://github.com/open-feature/java-sdk-contrib/issues/1193)) ([16a8287](https://github.com/open-feature/java-sdk-contrib/commit/16a8287d8ac4e952f4bb934bd731e4301ce3c261))

## [0.4.1](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.go-feature-flag-v0.4.0...dev.openfeature.contrib.providers.go-feature-flag-v0.4.1) (2025-01-20)


### 🐛 Bug Fixes

* **deps:** update dependency com.fasterxml.jackson.core:jackson-databind to v2.18.2 ([#1089](https://github.com/open-feature/java-sdk-contrib/issues/1089)) ([9b40e22](https://github.com/open-feature/java-sdk-contrib/commit/9b40e22e57739c7da417f834dd4f6822e6657ca8))
* **deps:** update dependency com.fasterxml.jackson.datatype:jackson-datatype-jsr310 to v2.18.2 ([#1088](https://github.com/open-feature/java-sdk-contrib/issues/1088)) ([34ec5a8](https://github.com/open-feature/java-sdk-contrib/commit/34ec5a83685100ac014def681286c59f0f939188))
* **deps:** update dependency io.reactivex.rxjava3:rxjava to v3.1.10 ([#1083](https://github.com/open-feature/java-sdk-contrib/issues/1083)) ([67d6308](https://github.com/open-feature/java-sdk-contrib/commit/67d63081c8b5f56ce7ac32a30f2bc1e67b6afe5d))


### ✨ New Features

* **go-feature-flag:** Support Exporter Metadata ([#1167](https://github.com/open-feature/java-sdk-contrib/issues/1167)) ([b55a52e](https://github.com/open-feature/java-sdk-contrib/commit/b55a52ecd90b53d25ea259e16fa0ef43fac9dc12))


### 🧹 Chore

* **deps:** update dependency org.apache.logging.log4j:log4j-slf4j2-impl to v2.24.2 ([#1079](https://github.com/open-feature/java-sdk-contrib/issues/1079)) ([804cd45](https://github.com/open-feature/java-sdk-contrib/commit/804cd455d6e9e79e1fa72b003245027ed7450487))
* **deps:** update dependency org.apache.logging.log4j:log4j-slf4j2-impl to v2.24.3 ([#1103](https://github.com/open-feature/java-sdk-contrib/issues/1103)) ([e2b8160](https://github.com/open-feature/java-sdk-contrib/commit/e2b8160dda2b82b43f665753187ab85a4e1abe13))

## [0.4.0](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.go-feature-flag-v0.3.0...dev.openfeature.contrib.providers.go-feature-flag-v0.4.0) (2024-11-21)


### ⚠ BREAKING CHANGES

* changing cache provider to caffeine over guava ([#1065](https://github.com/open-feature/java-sdk-contrib/issues/1065))

### 🐛 Bug Fixes

* **deps:** update dependency com.fasterxml.jackson.core:jackson-databind to v2.18.0 ([#979](https://github.com/open-feature/java-sdk-contrib/issues/979)) ([7e1a13e](https://github.com/open-feature/java-sdk-contrib/commit/7e1a13ec79b82f8fa49703af58087fea1874cea5))
* **deps:** update dependency com.fasterxml.jackson.core:jackson-databind to v2.18.1 ([#1047](https://github.com/open-feature/java-sdk-contrib/issues/1047)) ([a2ee3e6](https://github.com/open-feature/java-sdk-contrib/commit/a2ee3e6ed0c15c3ebaf55adc10198760f51a4a30))
* **deps:** update dependency com.fasterxml.jackson.datatype:jackson-datatype-jsr310 to v2.18.0 ([#980](https://github.com/open-feature/java-sdk-contrib/issues/980)) ([4d7f548](https://github.com/open-feature/java-sdk-contrib/commit/4d7f5489f64098b72024cd7e7a69409f3258517d))
* **deps:** update dependency com.fasterxml.jackson.datatype:jackson-datatype-jsr310 to v2.18.1 ([#1048](https://github.com/open-feature/java-sdk-contrib/issues/1048)) ([ac1a952](https://github.com/open-feature/java-sdk-contrib/commit/ac1a95239229def83a253515b4c88ee8b9186cf1))
* **deps:** update dependency com.google.guava:guava to v33.3.1-jre ([#965](https://github.com/open-feature/java-sdk-contrib/issues/965)) ([4288ca3](https://github.com/open-feature/java-sdk-contrib/commit/4288ca3901e811edbe2527ebedfcc7b1db95db02))


### ✨ New Features

* changing cache provider to caffeine over guava ([#1065](https://github.com/open-feature/java-sdk-contrib/issues/1065)) ([7083586](https://github.com/open-feature/java-sdk-contrib/commit/70835860090175209b6cec1cab6443d0bc4784fa))


### 🧹 Chore

* **deps:** update dependency org.apache.logging.log4j:log4j-slf4j2-impl to v2.24.1 ([#986](https://github.com/open-feature/java-sdk-contrib/issues/986)) ([1e53431](https://github.com/open-feature/java-sdk-contrib/commit/1e53431353c1de0856db6bdb815d2218d9ac94a2))

## [0.3.0](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.go-feature-flag-v0.2.23...dev.openfeature.contrib.providers.go-feature-flag-v0.3.0) (2024-09-25)


### ⚠ BREAKING CHANGES

* use sdk-maintained state, require 1.12 ([#964](https://github.com/open-feature/java-sdk-contrib/issues/964))

### 🐛 Bug Fixes

* **deps:** update dependency com.fasterxml.jackson.core:jackson-databind to v2.17.2 ([#866](https://github.com/open-feature/java-sdk-contrib/issues/866)) ([cf66811](https://github.com/open-feature/java-sdk-contrib/commit/cf668118351120b8a86b08f30facb38f7ec51086))
* **deps:** update dependency com.fasterxml.jackson.datatype:jackson-datatype-jsr310 to v2.17.2 ([#867](https://github.com/open-feature/java-sdk-contrib/issues/867)) ([84f534c](https://github.com/open-feature/java-sdk-contrib/commit/84f534c7d0a8a739b1d071a2f7c93c6ec21316da))
* **deps:** update dependency com.google.guava:guava to v33.3.0-jre ([#919](https://github.com/open-feature/java-sdk-contrib/issues/919)) ([ba4a7f9](https://github.com/open-feature/java-sdk-contrib/commit/ba4a7f91c04116eb86bcb12c3e0d82dfa4f5a099))
* **deps:** update dependency io.reactivex.rxjava3:rxjava to v3.1.9 ([#916](https://github.com/open-feature/java-sdk-contrib/issues/916)) ([6a3545a](https://github.com/open-feature/java-sdk-contrib/commit/6a3545a1f278297b8993df055b4ba80155e2925e))
* **deps:** update dependency org.slf4j:slf4j-api to v2.0.14 ([#904](https://github.com/open-feature/java-sdk-contrib/issues/904)) ([028b332](https://github.com/open-feature/java-sdk-contrib/commit/028b332dc8ac3b134e5453d5449a4c11b4ef250a))
* **deps:** update dependency org.slf4j:slf4j-api to v2.0.15 ([#910](https://github.com/open-feature/java-sdk-contrib/issues/910)) ([2f58638](https://github.com/open-feature/java-sdk-contrib/commit/2f58638eb4907c948325d1e61853e1b6eabfa4c1))
* **deps:** update dependency org.slf4j:slf4j-api to v2.0.16 ([#912](https://github.com/open-feature/java-sdk-contrib/issues/912)) ([52571d8](https://github.com/open-feature/java-sdk-contrib/commit/52571d806e7c547006db836245b4895fe9bc4660))


### ✨ New Features

* use sdk-maintained state, require 1.12 ([#964](https://github.com/open-feature/java-sdk-contrib/issues/964)) ([4a041b0](https://github.com/open-feature/java-sdk-contrib/commit/4a041b0dda9c4e460f4c2199f3bc680df0dda621))


### 🧹 Chore

* **deps:** update dependency org.apache.logging.log4j:log4j-slf4j2-impl to v2.24.0 ([#940](https://github.com/open-feature/java-sdk-contrib/issues/940)) ([5465337](https://github.com/open-feature/java-sdk-contrib/commit/546533739b453988720bb051d5e623ac7eb0b588))

## [0.2.23](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.go-feature-flag-v0.2.22...dev.openfeature.contrib.providers.go-feature-flag-v0.2.23) (2024-07-03)


### 🐛 Bug Fixes

* **gofeatureflag:** Fix NPE when error code is not set in the API response ([#855](https://github.com/open-feature/java-sdk-contrib/issues/855)) ([79ca933](https://github.com/open-feature/java-sdk-contrib/commit/79ca933f6b8ffac13ee46c0685f5675bccbb7dcd))

## [0.2.22](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.go-feature-flag-v0.2.21...dev.openfeature.contrib.providers.go-feature-flag-v0.2.22) (2024-06-20)


### 🐛 Bug Fixes

* **gofeatureflag:** fix java.lang.NoClassDefFoundError ([#839](https://github.com/open-feature/java-sdk-contrib/issues/839)) ([6859fa6](https://github.com/open-feature/java-sdk-contrib/commit/6859fa62ecb6dcf01f3927b72e2d964b5871b45a))

## [0.2.21](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.go-feature-flag-v0.2.20...dev.openfeature.contrib.providers.go-feature-flag-v0.2.21) (2024-06-19)


### 🐛 Bug Fixes

* **deps:** update dependency com.google.guava:guava to v33.2.1-jre ([#803](https://github.com/open-feature/java-sdk-contrib/issues/803)) ([4181807](https://github.com/open-feature/java-sdk-contrib/commit/418180789db84fb7cff160ef3af2cac773274aaa))


### ✨ New Features

* **gofeatureflag:** Add support of flag change cache removal ([#821](https://github.com/open-feature/java-sdk-contrib/issues/821)) ([536de91](https://github.com/open-feature/java-sdk-contrib/commit/536de91d64ff37330245070b55db42d0d308f02c))


### 📚 Documentation

* **GOFF:** Fix issues in readme ([#814](https://github.com/open-feature/java-sdk-contrib/issues/814)) ([5d60be7](https://github.com/open-feature/java-sdk-contrib/commit/5d60be72a4e583829438db7776826d21bf87bb82))

## [0.2.20](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.go-feature-flag-v0.2.19...dev.openfeature.contrib.providers.go-feature-flag-v0.2.20) (2024-05-06)


### 🐛 Bug Fixes

* **deps:** update dependency com.fasterxml.jackson.core:jackson-databind to v2.17.1 ([#777](https://github.com/open-feature/java-sdk-contrib/issues/777)) ([8b582d6](https://github.com/open-feature/java-sdk-contrib/commit/8b582d6052fd22b8141a9765b2a1a261933fd3a2))
* **deps:** update dependency com.fasterxml.jackson.datatype:jackson-datatype-jsr310 to v2.17.1 ([#778](https://github.com/open-feature/java-sdk-contrib/issues/778)) ([a4c4672](https://github.com/open-feature/java-sdk-contrib/commit/a4c467267caf35a838b0c2c6423fa01e21846c1d))
* **deps:** update dependency com.google.guava:guava to v33.2.0-jre ([#771](https://github.com/open-feature/java-sdk-contrib/issues/771)) ([c9e4245](https://github.com/open-feature/java-sdk-contrib/commit/c9e42451ccfec4f4af1c2dc54077bca3a444f75f))
* **deps:** update dependency org.slf4j:slf4j-api to v2.0.13 ([#752](https://github.com/open-feature/java-sdk-contrib/issues/752)) ([b820fcf](https://github.com/open-feature/java-sdk-contrib/commit/b820fcf1b7ea945a8e450dcc90addb82f5fb865d))

## [0.2.19](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.go-feature-flag-v0.2.18...dev.openfeature.contrib.providers.go-feature-flag-v0.2.19) (2024-04-01)


### 🐛 Bug Fixes

* **deps:** update dependency com.fasterxml.jackson.core:jackson-databind to v2.16.2 ([#707](https://github.com/open-feature/java-sdk-contrib/issues/707)) ([2ce424d](https://github.com/open-feature/java-sdk-contrib/commit/2ce424dd780a04c49efe29093a33bd26d0ceccc5))
* **deps:** update dependency com.fasterxml.jackson.core:jackson-databind to v2.17.0 ([#714](https://github.com/open-feature/java-sdk-contrib/issues/714)) ([a5964f0](https://github.com/open-feature/java-sdk-contrib/commit/a5964f0654124b668e50a5df7cf82c1028457f95))
* **deps:** update dependency com.fasterxml.jackson.datatype:jackson-datatype-jsr310 to v2.16.2 ([#708](https://github.com/open-feature/java-sdk-contrib/issues/708)) ([b6c7da1](https://github.com/open-feature/java-sdk-contrib/commit/b6c7da1ae902b74d9a92cf1f3a1cd105f9f2065c))
* **deps:** update dependency com.fasterxml.jackson.datatype:jackson-datatype-jsr310 to v2.17.0 ([#715](https://github.com/open-feature/java-sdk-contrib/issues/715)) ([95c5071](https://github.com/open-feature/java-sdk-contrib/commit/95c507175186bbffe786cfbaf6fb64295e244f52))
* **deps:** update dependency com.google.guava:guava to v33.1.0-jre ([#717](https://github.com/open-feature/java-sdk-contrib/issues/717)) ([ab405e8](https://github.com/open-feature/java-sdk-contrib/commit/ab405e834a2ebd9c68ce1c97236a5f5e81c4de18))
* **deps:** update dependency org.slf4j:slf4j-api to v2.0.10 ([#610](https://github.com/open-feature/java-sdk-contrib/issues/610)) ([ce3825a](https://github.com/open-feature/java-sdk-contrib/commit/ce3825af03beb0ec682eec390efd4cfff973bc99))
* **deps:** update dependency org.slf4j:slf4j-api to v2.0.11 ([#619](https://github.com/open-feature/java-sdk-contrib/issues/619)) ([178fd42](https://github.com/open-feature/java-sdk-contrib/commit/178fd42d314bb7f7018d70d532020a366cc58ae3))
* **deps:** update dependency org.slf4j:slf4j-api to v2.0.12 ([#661](https://github.com/open-feature/java-sdk-contrib/issues/661)) ([f03d933](https://github.com/open-feature/java-sdk-contrib/commit/f03d93305bda8ea932831e81db57c989ce4e14e4))
* potential finalizer attack ([#702](https://github.com/open-feature/java-sdk-contrib/issues/702)) ([572df60](https://github.com/open-feature/java-sdk-contrib/commit/572df60e3d4ef2d6039a8b2cd8554423179ffc30))


### 🧹 Chore

* **deps:** update dependency org.apache.logging.log4j:log4j-slf4j2-impl to v2.23.0 ([#689](https://github.com/open-feature/java-sdk-contrib/issues/689)) ([6589871](https://github.com/open-feature/java-sdk-contrib/commit/65898713166b5d02f246302c54fd7400ee4238d5))
* **deps:** update dependency org.apache.logging.log4j:log4j-slf4j2-impl to v2.23.1 ([#709](https://github.com/open-feature/java-sdk-contrib/issues/709)) ([d0bc7a5](https://github.com/open-feature/java-sdk-contrib/commit/d0bc7a5aceb746d6d7c442e189a6a1e011673ba7))

## [0.2.18](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.go-feature-flag-v0.2.17...dev.openfeature.contrib.providers.go-feature-flag-v0.2.18) (2023-12-28)


### 🐛 Bug Fixes

* [go-feature-flag] Fix NullPointerException on checking anonymous user ([#609](https://github.com/open-feature/java-sdk-contrib/issues/609)) ([9effb7d](https://github.com/open-feature/java-sdk-contrib/commit/9effb7d85e0b3594c18456609ed742acb3cf31c0))


### 🧹 Chore

* **deps:** update dependency org.apache.logging.log4j:log4j-slf4j2-impl to v2.22.1 ([#607](https://github.com/open-feature/java-sdk-contrib/issues/607)) ([75f0e3f](https://github.com/open-feature/java-sdk-contrib/commit/75f0e3f63a0f49d1d90de819145e480cd8eb4b6a))

## [0.2.17](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.go-feature-flag-v0.2.16...dev.openfeature.contrib.providers.go-feature-flag-v0.2.17) (2023-12-24)


### 🐛 Bug Fixes

* **deps:** update dependency com.fasterxml.jackson.core:jackson-databind to v2.16.1 ([#604](https://github.com/open-feature/java-sdk-contrib/issues/604)) ([165b1db](https://github.com/open-feature/java-sdk-contrib/commit/165b1db0a793b628ab6dd05b161350310505e41d))
* **deps:** update dependency com.fasterxml.jackson.datatype:jackson-datatype-jsr310 to v2.16.1 ([#605](https://github.com/open-feature/java-sdk-contrib/issues/605)) ([f403c64](https://github.com/open-feature/java-sdk-contrib/commit/f403c64973d851ac133f820f14f7ca658b4969ed))
* **deps:** update dependency com.google.guava:guava to v33 ([#595](https://github.com/open-feature/java-sdk-contrib/issues/595)) ([ec3e948](https://github.com/open-feature/java-sdk-contrib/commit/ec3e9489e50b15a2c13d0f4739f7bc940a52b961))

## [0.2.16](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.go-feature-flag-v0.2.15...dev.openfeature.contrib.providers.go-feature-flag-v0.2.16) (2023-12-07)


### 🐛 Bug Fixes

* [go-feature-flag] Make flag metadata optional ([#572](https://github.com/open-feature/java-sdk-contrib/issues/572)) ([6a70bc4](https://github.com/open-feature/java-sdk-contrib/commit/6a70bc41c1d7573ca0d3f8ed68d447f865beed05))


### 🧹 Chore

* **deps:** update dependency org.apache.logging.log4j:log4j-slf4j2-impl to v2.22.0 ([#540](https://github.com/open-feature/java-sdk-contrib/issues/540)) ([01d379f](https://github.com/open-feature/java-sdk-contrib/commit/01d379fc720c14c1fd1b6baeba23f3ab7007e740))

## [0.2.15](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.go-feature-flag-v0.2.14...dev.openfeature.contrib.providers.go-feature-flag-v0.2.15) (2023-11-16)


### 🐛 Bug Fixes

* **deps:** update dependency com.fasterxml.jackson.core:jackson-databind to v2.16.0 ([#538](https://github.com/open-feature/java-sdk-contrib/issues/538)) ([4857448](https://github.com/open-feature/java-sdk-contrib/commit/485744828ee3c2dc772ada94cb443b516adc9f78))
* **deps:** update dependency com.fasterxml.jackson.datatype:jackson-datatype-jsr310 to v2.16.0 ([#539](https://github.com/open-feature/java-sdk-contrib/issues/539)) ([30602a1](https://github.com/open-feature/java-sdk-contrib/commit/30602a1c147a91009e2e9c34c136954c160458d7))


### 🧹 Chore

* **deps:** update dependency org.apache.logging.log4j:log4j-slf4j2-impl to v2.21.1 ([#515](https://github.com/open-feature/java-sdk-contrib/issues/515)) ([f480b4f](https://github.com/open-feature/java-sdk-contrib/commit/f480b4f4e8e3777849233ed6fe1d15f1dd2acce4))

## [0.2.14](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.go-feature-flag-v0.2.13...dev.openfeature.contrib.providers.go-feature-flag-v0.2.14) (2023-10-20)


### 🐛 Bug Fixes

* **deps:** update dependency com.fasterxml.jackson.core:jackson-databind to v2.15.3 ([#493](https://github.com/open-feature/java-sdk-contrib/issues/493)) ([f6cb68f](https://github.com/open-feature/java-sdk-contrib/commit/f6cb68f3c54e67a10f7e2f5bf9f5a2a840689491))
* **deps:** update dependency com.fasterxml.jackson.datatype:jackson-datatype-jsr310 to v2.15.3 ([#494](https://github.com/open-feature/java-sdk-contrib/issues/494)) ([caed781](https://github.com/open-feature/java-sdk-contrib/commit/caed781e76d20a7d383c6842fecbace3dbbdfcd3))
* **deps:** update dependency com.google.guava:guava to v32.1.3-jre ([#484](https://github.com/open-feature/java-sdk-contrib/issues/484)) ([b197f3b](https://github.com/open-feature/java-sdk-contrib/commit/b197f3b904256fff7b0a3987c33c5dc22fe9f12a))
* **deps:** update dependency com.squareup.okhttp3:okhttp to v4.12.0 ([#503](https://github.com/open-feature/java-sdk-contrib/issues/503)) ([a82668c](https://github.com/open-feature/java-sdk-contrib/commit/a82668cd6ad4a4026626c67d69ea84c89f617e3b))


### 🧹 Chore

* **deps:** update dependency com.squareup.okhttp3:mockwebserver to v4.12.0 ([#502](https://github.com/open-feature/java-sdk-contrib/issues/502)) ([4e0b6ff](https://github.com/open-feature/java-sdk-contrib/commit/4e0b6fffdfdae425be5015440ba0434879db2554))
* **deps:** update dependency org.apache.logging.log4j:log4j-slf4j2-impl to v2.21.0 ([#501](https://github.com/open-feature/java-sdk-contrib/issues/501)) ([2f2ce59](https://github.com/open-feature/java-sdk-contrib/commit/2f2ce590b3589331f9b4c99bd7a18cf53c7436d8))

## [0.2.13](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.go-feature-flag-v0.2.12...dev.openfeature.contrib.providers.go-feature-flag-v0.2.13) (2023-09-21)


### Bug Fixes

* Double value for a flag converted to integer ([#442](https://github.com/open-feature/java-sdk-contrib/issues/442)) ([3881ebe](https://github.com/open-feature/java-sdk-contrib/commit/3881ebe4b1e8bf32d80c0d2c96b4da1cbf30f1df))

## [0.2.12](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.go-feature-flag-v0.2.11...dev.openfeature.contrib.providers.go-feature-flag-v0.2.12) (2023-09-12)


### Bug Fixes

* **deps:** update dependency com.google.guava:guava to v32.1.2-jre ([#392](https://github.com/open-feature/java-sdk-contrib/issues/392)) ([34b2d86](https://github.com/open-feature/java-sdk-contrib/commit/34b2d86f2ff9ff3b9678bbb76b04b37cefe8f4c0))
* **deps:** update dependency org.slf4j:slf4j-api to v2.0.9 ([#413](https://github.com/open-feature/java-sdk-contrib/issues/413)) ([757a8db](https://github.com/open-feature/java-sdk-contrib/commit/757a8db28f52dd2084845465e403cf43aea6e537))

## [0.2.11](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.go-feature-flag-v0.2.10...dev.openfeature.contrib.providers.go-feature-flag-v0.2.11) (2023-08-07)


### Features

* ISSUE-658 go-feature-flag sdk - add cache ([#369](https://github.com/open-feature/java-sdk-contrib/issues/369)) ([5e6f4a1](https://github.com/open-feature/java-sdk-contrib/commit/5e6f4a15e3db0250593ba8a93f244161a399efe6))

## [0.2.10](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.go-feature-flag-v0.2.9...dev.openfeature.contrib.providers.go-feature-flag-v0.2.10) (2023-08-02)


### Features

* [go-feature-flag] Support flag metadata ([#367](https://github.com/open-feature/java-sdk-contrib/issues/367)) ([557074e](https://github.com/open-feature/java-sdk-contrib/commit/557074ef6a6bfea1be34fdcc4b055440eca49b17))

## [0.2.9](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.go-feature-flag-v0.2.8...dev.openfeature.contrib.providers.go-feature-flag-v0.2.9) (2023-05-31)


### Bug Fixes

* **deps:** update dependency com.fasterxml.jackson.core:jackson-databind to v2.15.2 ([#333](https://github.com/open-feature/java-sdk-contrib/issues/333)) ([c0259fe](https://github.com/open-feature/java-sdk-contrib/commit/c0259fe87b8cf28adc12512af6738e0241b17fbd))
* **deps:** update dependency com.fasterxml.jackson.datatype:jackson-datatype-jsr310 to v2.15.2 ([#334](https://github.com/open-feature/java-sdk-contrib/issues/334)) ([17e8746](https://github.com/open-feature/java-sdk-contrib/commit/17e8746044d6700d0592053467ab75103c03f68e))

## [0.2.8](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.go-feature-flag-v0.2.7...dev.openfeature.contrib.providers.go-feature-flag-v0.2.8) (2023-05-26)


### Bug Fixes

* **deps:** update dependency com.fasterxml.jackson.datatype:jackson-datatype-jsr310 to v2.15.1 ([#317](https://github.com/open-feature/java-sdk-contrib/issues/317)) ([01e254d](https://github.com/open-feature/java-sdk-contrib/commit/01e254dbcda94143b195bf108ddce34118b9c914))

## [0.2.7](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.go-feature-flag-v0.2.6...dev.openfeature.contrib.providers.go-feature-flag-v0.2.7) (2023-05-17)


### Bug Fixes

* **deps:** update dependency com.fasterxml.jackson.core:jackson-databind to v2.15.1 ([#316](https://github.com/open-feature/java-sdk-contrib/issues/316)) ([59df8cc](https://github.com/open-feature/java-sdk-contrib/commit/59df8ccc888080b3a332131a4c407b7e12cc1158))

## [0.2.6](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.go-feature-flag-v0.2.5...dev.openfeature.contrib.providers.go-feature-flag-v0.2.6) (2023-05-06)


### Bug Fixes

* **deps:** update dependency com.fasterxml.jackson.core:jackson-databind to v2.15.0 ([#292](https://github.com/open-feature/java-sdk-contrib/issues/292)) ([d59c34b](https://github.com/open-feature/java-sdk-contrib/commit/d59c34bbee6f49580012c37802ded78d19f74db2))
* **deps:** update dependency com.fasterxml.jackson.datatype:jackson-datatype-jsr310 to v2.15.0 ([#293](https://github.com/open-feature/java-sdk-contrib/issues/293)) ([f7f6781](https://github.com/open-feature/java-sdk-contrib/commit/f7f67815a1bbeac836c0b8c04d510c51daf745ca))
* **deps:** update dependency com.squareup.okhttp3:okhttp to v4.11.0 ([#290](https://github.com/open-feature/java-sdk-contrib/issues/290)) ([b7be0ec](https://github.com/open-feature/java-sdk-contrib/commit/b7be0ec99be5cd645cf5b5033ad2b1093a45d63e))

## [0.2.5](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.go-feature-flag-v0.2.4...dev.openfeature.contrib.providers.go-feature-flag-v0.2.5) (2023-04-07)


### Features

* Support apiKey for GO Feature Flag relay proxy v1.7.0 ([#270](https://github.com/open-feature/java-sdk-contrib/issues/270)) ([83d9000](https://github.com/open-feature/java-sdk-contrib/commit/83d9000497c3bcfc544c0a38e57c32a4ebe5e0bb))

## [0.2.4](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.go-feature-flag-v0.2.3...dev.openfeature.contrib.providers.go-feature-flag-v0.2.4) (2023-03-21)


### Bug Fixes

* attach original exception when throwing a general error ([#247](https://github.com/open-feature/java-sdk-contrib/issues/247)) ([cc5d7e5](https://github.com/open-feature/java-sdk-contrib/commit/cc5d7e56b9aecba075fc1515e035e9fc77bb1c9f))
* issue on unknown field ([#246](https://github.com/open-feature/java-sdk-contrib/issues/246)) ([2b7163a](https://github.com/open-feature/java-sdk-contrib/commit/2b7163a3f1392251d7268efa5751156ba8c93694))

## [0.2.3](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.go-feature-flag-v0.2.2...dev.openfeature.contrib.providers.go-feature-flag-v0.2.3) (2023-02-07)


### Bug Fixes

* **deps:** update dependency com.fasterxml.jackson.core:jackson-databind to v2.14.2 ([#207](https://github.com/open-feature/java-sdk-contrib/issues/207)) ([b5f0d3d](https://github.com/open-feature/java-sdk-contrib/commit/b5f0d3daa2a19a7b51648a6644eba9973f154be6))
* **deps:** update dependency com.fasterxml.jackson.datatype:jackson-datatype-jsr310 to v2.14.2 ([#208](https://github.com/open-feature/java-sdk-contrib/issues/208)) ([7c1ff7a](https://github.com/open-feature/java-sdk-contrib/commit/7c1ff7a731e7ce13126095e7d239709445929e5f))

## [0.2.2](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.go-feature-flag-v0.2.1...dev.openfeature.contrib.providers.go-feature-flag-v0.2.2) (2022-12-22)


### Bug Fixes

* **deps:** update dependency com.fasterxml.jackson.core:jackson-databind to v2.14.1 ([#145](https://github.com/open-feature/java-sdk-contrib/issues/145)) ([a17eb44](https://github.com/open-feature/java-sdk-contrib/commit/a17eb44e2ccdd2eddd2be996ea2cc7141fc14e9a))
* **deps:** update dependency com.fasterxml.jackson.datatype:jackson-datatype-jsr310 to v2.14.1 ([#146](https://github.com/open-feature/java-sdk-contrib/issues/146)) ([42d172c](https://github.com/open-feature/java-sdk-contrib/commit/42d172c6539b82e75de61028683a35a6ae08ece9))

## [0.2.1](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.go-feature-flag-v0.2.0...dev.openfeature.contrib.providers.go-feature-flag-v0.2.1) (2022-11-17)


### Bug Fixes

* GO Feature Flag provider fix issues + compatible with SDK 0.1.0 ([#132](https://github.com/open-feature/java-sdk-contrib/issues/132)) ([046947d](https://github.com/open-feature/java-sdk-contrib/commit/046947dc58fa717d0bfa6d5c516261fe79fd9e9e))

## [0.2.0](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.go-feature-flag-v0.1.1...dev.openfeature.contrib.providers.go-feature-flag-v0.2.0) (2022-10-27)


### ⚠ BREAKING CHANGES

* use 1.0 sdk (#123)

### Miscellaneous Chores

* use 1.0 sdk ([#123](https://github.com/open-feature/java-sdk-contrib/issues/123)) ([ee78f61](https://github.com/open-feature/java-sdk-contrib/commit/ee78f610f669eff6f90ffc958e1be88ed203350f))

## [0.1.1](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.go-feature-flag-v0.1.0...dev.openfeature.contrib.providers.go-feature-flag-v0.1.1) (2022-10-13)


### Bug Fixes

* update-parent-pom ([#120](https://github.com/open-feature/java-sdk-contrib/issues/120)) ([d8ac4bd](https://github.com/open-feature/java-sdk-contrib/commit/d8ac4bdba6b5d9efb98ea641d50337f0e3ba3139))

## [0.1.0](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.go-feature-flag-v0.0.3...dev.openfeature.contrib.providers.go-feature-flag-v0.1.0) (2022-10-13)


### ⚠ BREAKING CHANGES

* udpate to sdk 0.3.0
* update to sdk 0.3.0 (#116)

### Features

* udpate to sdk 0.3.0 ([8933bb9](https://github.com/open-feature/java-sdk-contrib/commit/8933bb9b4521e44572b67e6784fd7ce6c541d7b8))
* update to sdk 0.3.0 ([#116](https://github.com/open-feature/java-sdk-contrib/issues/116)) ([8933bb9](https://github.com/open-feature/java-sdk-contrib/commit/8933bb9b4521e44572b67e6784fd7ce6c541d7b8))

## [0.0.3](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.go-feature-flag-v0.0.2...dev.openfeature.contrib.providers.go-feature-flag-v0.0.3) (2022-09-27)


### Bug Fixes

* update javadoc ([8828cce](https://github.com/open-feature/java-sdk-contrib/commit/8828cceadf3571b25155dbd8d0d88589244ade2a))

## [0.0.2](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.go-feature-flag-v0.0.1...dev.openfeature.contrib.providers.go-feature-flag-v0.0.2) (2022-09-26)


### Features

* Go feature flag provider ([#106](https://github.com/open-feature/java-sdk-contrib/issues/106)) ([f793722](https://github.com/open-feature/java-sdk-contrib/commit/f7937223d21ee97ebab2f42e79cb264d2b77ed4a))
