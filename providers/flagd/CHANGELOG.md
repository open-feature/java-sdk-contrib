# Changelog

## [0.11.19](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.11.18...dev.openfeature.contrib.providers.flagd-v0.11.19) (2026-01-06)


### ✨ New Features

* Add FLAGD_SYNC_PORT support for in-process providers with backwards compatibility ([#1651](https://github.com/open-feature/java-sdk-contrib/issues/1651)) ([4f91f74](https://github.com/open-feature/java-sdk-contrib/commit/4f91f74f8f7125299ac2c5dd835a048507e359ef))
* add option to rebuild gRPC connection on error ([#1668](https://github.com/open-feature/java-sdk-contrib/issues/1668)) ([9444297](https://github.com/open-feature/java-sdk-contrib/commit/9444297f511ce584799e804f960b7d486c80570d))
* flagd provider creates named daemon threads for error executor ([#1625](https://github.com/open-feature/java-sdk-contrib/issues/1625)) ([ba277bf](https://github.com/open-feature/java-sdk-contrib/commit/ba277bf21dc3f4476bae2846a821b4655ede899f))
* **flagd:** Implement header-based selector for in-process sync stream connection [#1622](https://github.com/open-feature/java-sdk-contrib/issues/1622) ([#1623](https://github.com/open-feature/java-sdk-contrib/issues/1623)) ([630d470](https://github.com/open-feature/java-sdk-contrib/commit/630d470276f9c118b474b1dfb69f49b531c62cd5))
* **flagd:** introduce fatalStatusCodes option ([#1624](https://github.com/open-feature/java-sdk-contrib/issues/1624)) ([7018eea](https://github.com/open-feature/java-sdk-contrib/commit/7018eeae44282e265fdd34ababfa0f42810f475d))
* Improve flaky tests and add error messages ([#1653](https://github.com/open-feature/java-sdk-contrib/issues/1653)) ([48df358](https://github.com/open-feature/java-sdk-contrib/commit/48df358267d617c48ed5cefdaf6a0f680f3cfa1c))

## [0.11.18](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.11.17...dev.openfeature.contrib.providers.flagd-v0.11.18) (2025-11-17)


### 🐛 Bug Fixes

* possible tight busy loop on certain connection errors ([#1629](https://github.com/open-feature/java-sdk-contrib/issues/1629)) ([a2f5f28](https://github.com/open-feature/java-sdk-contrib/commit/a2f5f28509699bf6fb8684db6acc0dda5297b72a))

## [0.11.17](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.11.16...dev.openfeature.contrib.providers.flagd-v0.11.17) (2025-10-15)


### 🐛 Bug Fixes

* **security:** force netty-codec-http2 4.1.125 ([#1615](https://github.com/open-feature/java-sdk-contrib/issues/1615)) ([0b0070c](https://github.com/open-feature/java-sdk-contrib/commit/0b0070cacf79dfd475fb3ce0c0dbdffd7e02fe8a))

## [0.11.16](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.11.15...dev.openfeature.contrib.providers.flagd-v0.11.16) (2025-10-14)


### 🐛 Bug Fixes

* **flagd:** fix wrong environment variable and test execution ([#1589](https://github.com/open-feature/java-sdk-contrib/issues/1589)) ([e1d8e54](https://github.com/open-feature/java-sdk-contrib/commit/e1d8e54cb15f7e9f27626c60cebded3690a84698))
* **flagd:** improve stream observer, refine retry policy; don't use retry to avoid busy loop ([#1590](https://github.com/open-feature/java-sdk-contrib/issues/1590)) ([791f38c](https://github.com/open-feature/java-sdk-contrib/commit/791f38cdcdb12f7d7c8ec457ecea968f1ec5d048))

## [0.11.15](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.11.14...dev.openfeature.contrib.providers.flagd-v0.11.15) (2025-09-05)


### 🐛 Bug Fixes

* **flagd:** implement error code step and general error ([#1519](https://github.com/open-feature/java-sdk-contrib/issues/1519)) ([0985315](https://github.com/open-feature/java-sdk-contrib/commit/0985315388b6b553ec23a3f31b56b3b338ddedf9))


### ✨ New Features

* allowing null/missing default values ([#1511](https://github.com/open-feature/java-sdk-contrib/issues/1511)) ([229ddcb](https://github.com/open-feature/java-sdk-contrib/commit/229ddcb5a22fb14293c0649ce80d9ffd81c4e617))
* **flagd:** Adjust to disable-sync-metadata toggle in flagd ([#1549](https://github.com/open-feature/java-sdk-contrib/issues/1549)) ([f1adc5d](https://github.com/open-feature/java-sdk-contrib/commit/f1adc5dfe70eaa94a130b5ccaa18c1fc69daee7a))

## [0.11.14](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.11.13...dev.openfeature.contrib.providers.flagd-v0.11.14) (2025-07-14)


### 🐛 Bug Fixes

* **security:** update dependency org.apache.commons:commons-lang3 to v3.18.0 [security] ([#1512](https://github.com/open-feature/java-sdk-contrib/issues/1512)) ([d178006](https://github.com/open-feature/java-sdk-contrib/commit/d178006411b91ff05376ac8f604c5282f2f19383))

## [0.11.13](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.11.12...dev.openfeature.contrib.providers.flagd-v0.11.13) (2025-07-09)


### 🐛 Bug Fixes

* require latest SDK ([#1488](https://github.com/open-feature/java-sdk-contrib/issues/1488)) ([71afb81](https://github.com/open-feature/java-sdk-contrib/commit/71afb81703bc2a5350ab967e478c527469fdb5d2))

## [0.11.12](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.11.11...dev.openfeature.contrib.providers.flagd-v0.11.12) (2025-07-08)


### 🐛 Bug Fixes

* **deps:** update dependency com.networknt:json-schema-validator to v1.5.8 ([#1447](https://github.com/open-feature/java-sdk-contrib/issues/1447)) ([1fd425b](https://github.com/open-feature/java-sdk-contrib/commit/1fd425bba5eef44c4bde2e8546a2c0c094960dd3))
* **deps:** update junit-framework monorepo to v5.13.3 ([#1461](https://github.com/open-feature/java-sdk-contrib/issues/1461)) ([1aae615](https://github.com/open-feature/java-sdk-contrib/commit/1aae615131dc41106e75a915e3f2e8e2d31a96c4))

## [0.11.11](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.11.10...dev.openfeature.contrib.providers.flagd-v0.11.11) (2025-06-25)


### 🐛 Bug Fixes

* **deps:** update dependency io.github.jamsesso:json-logic-java to v1.1.0 ([#1373](https://github.com/open-feature/java-sdk-contrib/issues/1373)) ([f4c9e0c](https://github.com/open-feature/java-sdk-contrib/commit/f4c9e0c87ad40244bea635856b1fe675e95a7b79))
* **deps:** update dependency org.semver4j:semver4j to v5.8.0 ([#1435](https://github.com/open-feature/java-sdk-contrib/issues/1435)) ([525c72a](https://github.com/open-feature/java-sdk-contrib/commit/525c72a80b07d41e383d3bd7b3b55ec5fb7f9c4e))
* remove redundant metadata error logs within grace period ([#1441](https://github.com/open-feature/java-sdk-contrib/issues/1441)) ([7e87be3](https://github.com/open-feature/java-sdk-contrib/commit/7e87be34c6a322a01828e9f2cf3615de545be18b))


### 🧹 Chore

* **deps:** update dependency com.github.spotbugs:spotbugs-maven-plugin to v4.9.3.1 ([#1440](https://github.com/open-feature/java-sdk-contrib/issues/1440)) ([f183544](https://github.com/open-feature/java-sdk-contrib/commit/f1835441fdcb2f1a767ce7d51d008962f5feae9e))
* **deps:** update dependency org.junit.jupiter:junit-jupiter to v5.13.2 ([#1436](https://github.com/open-feature/java-sdk-contrib/issues/1436)) ([03b68fc](https://github.com/open-feature/java-sdk-contrib/commit/03b68fcb2245cdf946ed3cc8ac300e60660ac27b))
* **deps:** update providers/flagd/spec digest to 1965aae ([#1434](https://github.com/open-feature/java-sdk-contrib/issues/1434)) ([dd127c2](https://github.com/open-feature/java-sdk-contrib/commit/dd127c2288b0cf6a403e000e41025ed405b2d295))
* **deps:** update testcontainers-java monorepo to v1.21.2 ([#1430](https://github.com/open-feature/java-sdk-contrib/issues/1430)) ([46eed68](https://github.com/open-feature/java-sdk-contrib/commit/46eed68301c480a2b5dda969747115051a3fe0e9))

## [0.11.10](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.11.9...dev.openfeature.contrib.providers.flagd-v0.11.10) (2025-06-18)


### 🐛 Bug Fixes

* **deps:** update dependency io.netty:netty-transport-native-epoll to v4.2.2.final ([#1321](https://github.com/open-feature/java-sdk-contrib/issues/1321)) ([82abc12](https://github.com/open-feature/java-sdk-contrib/commit/82abc12e137f1a4bc8ac2fc78fc58b5d5c1586ca))
* **deps:** update dependency org.semver4j:semver4j to v5.7.1 ([#1404](https://github.com/open-feature/java-sdk-contrib/issues/1404)) ([dc02601](https://github.com/open-feature/java-sdk-contrib/commit/dc0260146c6b0ea0b0f3dbaa2dac5d30077153c1))
* **deps:** update jackson monorepo to v2.19.1 ([#1416](https://github.com/open-feature/java-sdk-contrib/issues/1416)) ([43630e5](https://github.com/open-feature/java-sdk-contrib/commit/43630e5da8fd1f7f8b8ccfcb2aa77cab51949be7))
* **deps:** update junit5 monorepo to v5.13.1 ([#1406](https://github.com/open-feature/java-sdk-contrib/issues/1406)) ([749d241](https://github.com/open-feature/java-sdk-contrib/commit/749d2418837a188e4ae58f852146fafa52693363))
* **deps:** update opentelemetry-java monorepo to v1.51.0 ([#1405](https://github.com/open-feature/java-sdk-contrib/issues/1405)) ([0eea5da](https://github.com/open-feature/java-sdk-contrib/commit/0eea5daa0115aecd66d5c2b946f48504e282afc5))
* Fix flaky SyncStreamQueueSourceTest test ([#1419](https://github.com/open-feature/java-sdk-contrib/issues/1419)) ([4d3d868](https://github.com/open-feature/java-sdk-contrib/commit/4d3d868cfeecfd35cfd7d53d703da645198f643b))
* stateBlockingQueue size increase to fix missed/delayed messages ([#1422](https://github.com/open-feature/java-sdk-contrib/issues/1422)) ([a3578cc](https://github.com/open-feature/java-sdk-contrib/commit/a3578ccf6acab740e632757638325a7c5247f338))


### 🧹 Chore

* added upper bound to parent pom range ([#1421](https://github.com/open-feature/java-sdk-contrib/issues/1421)) ([5701dc5](https://github.com/open-feature/java-sdk-contrib/commit/5701dc5b1b89ee0f245df9ea6284b5d327f40992))
* **deps:** update providers/flagd/spec digest to 42340bb ([#1407](https://github.com/open-feature/java-sdk-contrib/issues/1407)) ([eb0484c](https://github.com/open-feature/java-sdk-contrib/commit/eb0484c92ef954741b0c81d5ab22255242833fc3))
* **deps:** update providers/flagd/spec digest to bb2dc2c ([#1401](https://github.com/open-feature/java-sdk-contrib/issues/1401)) ([96848a0](https://github.com/open-feature/java-sdk-contrib/commit/96848a0f0363ef8cba618bc8916ad8128923f0d8))

## [0.11.9](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.11.8...dev.openfeature.contrib.providers.flagd-v0.11.9) (2025-06-04)


### 🐛 Bug Fixes

* **deps:** update dependency com.google.code.gson:gson to v2.13.1 ([#1318](https://github.com/open-feature/java-sdk-contrib/issues/1318)) ([4f47ff1](https://github.com/open-feature/java-sdk-contrib/commit/4f47ff16e2c11dd4ad2a5b2ac9927dd19b827c27))
* **deps:** update dependency com.networknt:json-schema-validator to v1.5.7 ([#1386](https://github.com/open-feature/java-sdk-contrib/issues/1386)) ([5bdb220](https://github.com/open-feature/java-sdk-contrib/commit/5bdb220e18ebbb1f764c816baca096fc8862bb69))
* **deps:** update dependency org.apache.commons:commons-collections4 to v4.5.0 ([#1322](https://github.com/open-feature/java-sdk-contrib/issues/1322)) ([325d876](https://github.com/open-feature/java-sdk-contrib/commit/325d876aec19bb674920afd22185d22b4274c549))
* **deps:** update dependency org.semver4j:semver4j to v5.7.0 ([#1374](https://github.com/open-feature/java-sdk-contrib/issues/1374)) ([eefe738](https://github.com/open-feature/java-sdk-contrib/commit/eefe738b27d80986a1dfda56ddf89765793aad62))
* **deps:** update grpc-java monorepo to v1.72.0 ([#1343](https://github.com/open-feature/java-sdk-contrib/issues/1343)) ([fc26ff2](https://github.com/open-feature/java-sdk-contrib/commit/fc26ff2a14e2891224088b59ad5f310f554dc81f))
* **deps:** update grpc-java monorepo to v1.73.0 ([#1390](https://github.com/open-feature/java-sdk-contrib/issues/1390)) ([ab5cf26](https://github.com/open-feature/java-sdk-contrib/commit/ab5cf2697fffc4b10aa2b840a3b6c260433f5de7))
* **deps:** update jackson monorepo to v2.19.0 ([#1346](https://github.com/open-feature/java-sdk-contrib/issues/1346)) ([d4af23b](https://github.com/open-feature/java-sdk-contrib/commit/d4af23b9f2700233ea22f9132df00637820da10c))
* **deps:** update junit5 monorepo to v5.12.2 ([#1313](https://github.com/open-feature/java-sdk-contrib/issues/1313)) ([ab14cc1](https://github.com/open-feature/java-sdk-contrib/commit/ab14cc189a3c5a4b46d02cf87fcf683c5b7ff2c3))
* **deps:** update junit5 monorepo to v5.13.0 ([#1394](https://github.com/open-feature/java-sdk-contrib/issues/1394)) ([943064e](https://github.com/open-feature/java-sdk-contrib/commit/943064e894134ba95ce8e7f7ddb805c1de7063de))
* **deps:** update opentelemetry-java monorepo to v1.49.0 ([#1344](https://github.com/open-feature/java-sdk-contrib/issues/1344)) ([045a12c](https://github.com/open-feature/java-sdk-contrib/commit/045a12c6a66869a359c86cde6bc9f5217a89bc87))
* **deps:** update opentelemetry-java monorepo to v1.50.0 ([#1375](https://github.com/open-feature/java-sdk-contrib/issues/1375)) ([748c139](https://github.com/open-feature/java-sdk-contrib/commit/748c139a9497f9a49e29e68318b674b56b61e24d))


### ✨ New Features

* migrate to Java 11 ([#1336](https://github.com/open-feature/java-sdk-contrib/issues/1336)) ([a4be1ff](https://github.com/open-feature/java-sdk-contrib/commit/a4be1ff66870a72189873171e83c5b65dbb9991c))


### 🧹 Chore

* add tobuilder test ([#1289](https://github.com/open-feature/java-sdk-contrib/issues/1289)) ([2e360bb](https://github.com/open-feature/java-sdk-contrib/commit/2e360bbf6e54c21f331525b59b5b63deb57cbc0f))
* **deps:** update dependency com.diffplug.spotless:spotless-maven-plugin to v2.44.4 ([#1302](https://github.com/open-feature/java-sdk-contrib/issues/1302)) ([e80fb3e](https://github.com/open-feature/java-sdk-contrib/commit/e80fb3e2b2f0ff80432f127067d44ef0ef0de0b8))
* **deps:** update dependency io.rest-assured:rest-assured to v5.5.2 ([#1370](https://github.com/open-feature/java-sdk-contrib/issues/1370)) ([40cd38c](https://github.com/open-feature/java-sdk-contrib/commit/40cd38c1be2925694b41051318ddf445c10c01ab))
* **deps:** update dependency io.rest-assured:rest-assured to v5.5.5 ([#1383](https://github.com/open-feature/java-sdk-contrib/issues/1383)) ([74774b9](https://github.com/open-feature/java-sdk-contrib/commit/74774b984a00a84d5db615c2bddfcc3b4ab3926a))
* **deps:** update dependency org.codehaus.mojo:exec-maven-plugin to v3.5.1 ([#1389](https://github.com/open-feature/java-sdk-contrib/issues/1389)) ([c39c79e](https://github.com/open-feature/java-sdk-contrib/commit/c39c79ed50fdf62d5f31b64349f57067c9873694))
* **deps:** update dependency providers/flagd/test-harness to v2.8.0 ([#1316](https://github.com/open-feature/java-sdk-contrib/issues/1316)) ([0332ec2](https://github.com/open-feature/java-sdk-contrib/commit/0332ec2eac5c9b2fa864e2a58ffe4dc3b1c458d6))
* **deps:** update providers/flagd/schemas digest to 2852d77 ([#1391](https://github.com/open-feature/java-sdk-contrib/issues/1391)) ([7ae091c](https://github.com/open-feature/java-sdk-contrib/commit/7ae091c26d26c018a96e4b6470088c6b8deecbc6))
* **deps:** update providers/flagd/schemas digest to 9b0ee43 ([#1292](https://github.com/open-feature/java-sdk-contrib/issues/1292)) ([bcae7f7](https://github.com/open-feature/java-sdk-contrib/commit/bcae7f7d94783a583512e50fd652a88fd4d95471))
* **deps:** update providers/flagd/schemas digest to 9b0ee43 ([#1296](https://github.com/open-feature/java-sdk-contrib/issues/1296)) ([58fe5da](https://github.com/open-feature/java-sdk-contrib/commit/58fe5da7d4e2f6f4ae2c1caf3411a01e84a1dc1a))
* **deps:** update providers/flagd/schemas digest to c707f56 ([#1311](https://github.com/open-feature/java-sdk-contrib/issues/1311)) ([855b0e6](https://github.com/open-feature/java-sdk-contrib/commit/855b0e657c1d08275ed0251da4802232d88c80af))
* **deps:** update providers/flagd/spec digest to 130df3e ([#1294](https://github.com/open-feature/java-sdk-contrib/issues/1294)) ([c6b9e89](https://github.com/open-feature/java-sdk-contrib/commit/c6b9e8959b1ac514bbf31031418e0a2cca1ab81b))
* **deps:** update providers/flagd/spec digest to 18cde17 ([#1312](https://github.com/open-feature/java-sdk-contrib/issues/1312)) ([dca9656](https://github.com/open-feature/java-sdk-contrib/commit/dca965668793f31dcd3be10405af336ac8ce3aab))
* **deps:** update providers/flagd/spec digest to 27e4461 ([#1295](https://github.com/open-feature/java-sdk-contrib/issues/1295)) ([1586f04](https://github.com/open-feature/java-sdk-contrib/commit/1586f0482be643bcb0c1f4c2c7213d67e49f3233))
* **deps:** update providers/flagd/spec digest to 2ba05d8 ([#1327](https://github.com/open-feature/java-sdk-contrib/issues/1327)) ([5b511a4](https://github.com/open-feature/java-sdk-contrib/commit/5b511a45641bb30031d90290bc5ad976937c647d))
* **deps:** update providers/flagd/spec digest to 36944c6 ([#1317](https://github.com/open-feature/java-sdk-contrib/issues/1317)) ([a38d14b](https://github.com/open-feature/java-sdk-contrib/commit/a38d14bf6b88a631d3825bcf8474e01d109b7601))
* **deps:** update providers/flagd/spec digest to d27e000 ([#1345](https://github.com/open-feature/java-sdk-contrib/issues/1345)) ([6f4c1ae](https://github.com/open-feature/java-sdk-contrib/commit/6f4c1aedb010c58936dfedf58c0bd4c060799e06))
* **deps:** update providers/flagd/spec digest to edf0deb ([#1368](https://github.com/open-feature/java-sdk-contrib/issues/1368)) ([f5c9bc0](https://github.com/open-feature/java-sdk-contrib/commit/f5c9bc058810f0c87529c2837d4c2212d944621d))
* **deps:** update providers/flagd/spec digest to f014806 ([#1385](https://github.com/open-feature/java-sdk-contrib/issues/1385)) ([034ab85](https://github.com/open-feature/java-sdk-contrib/commit/034ab857bf989fa144e660e5f595e259aeab0c88))
* **deps:** update testcontainers-java monorepo to v1.21.0 ([#1320](https://github.com/open-feature/java-sdk-contrib/issues/1320)) ([b4b0d4f](https://github.com/open-feature/java-sdk-contrib/commit/b4b0d4fe565c1e9f81c2e899a5d97301be43bfe9))
* **deps:** update testcontainers-java monorepo to v1.21.1 ([#1392](https://github.com/open-feature/java-sdk-contrib/issues/1392)) ([1b01d0e](https://github.com/open-feature/java-sdk-contrib/commit/1b01d0e322e1c17461a47e6de11c174f767889b5))
* **flagd:** update testharness and add metadata tests ([#1293](https://github.com/open-feature/java-sdk-contrib/issues/1293)) ([3f13260](https://github.com/open-feature/java-sdk-contrib/commit/3f132601f32ef1741b29beee04de4a3de2ab3c86))
* loosen parent version req ([#1341](https://github.com/open-feature/java-sdk-contrib/issues/1341)) ([4c7b584](https://github.com/open-feature/java-sdk-contrib/commit/4c7b58413b47db5c8c52b906ec2cbbc846779199))
* update component owners and flagd readme ([#1372](https://github.com/open-feature/java-sdk-contrib/issues/1372)) ([2dea3b2](https://github.com/open-feature/java-sdk-contrib/commit/2dea3b296d7eb63c14f612949e6c533629b8eb42))
* use parent 0.2.1 ([17926bf](https://github.com/open-feature/java-sdk-contrib/commit/17926bf37790d31cb34b2f5d521e0ed46e4af93f))

## [0.11.8](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.11.7...dev.openfeature.contrib.providers.flagd-v0.11.8) (2025-03-18)


### 🐛 Bug Fixes

* **deps:** update dependency io.github.jamsesso:json-logic-java to v1.0.9 ([#1282](https://github.com/open-feature/java-sdk-contrib/issues/1282)) ([1ddc63c](https://github.com/open-feature/java-sdk-contrib/commit/1ddc63c3a5e894d42bd6097f9936fb0e9431d296))
* **deps:** update junit5 monorepo to v5.12.1 ([#1279](https://github.com/open-feature/java-sdk-contrib/issues/1279)) ([63062c2](https://github.com/open-feature/java-sdk-contrib/commit/63062c2fcca49721792753143190ed0c26785f1d))


### ✨ New Features

* **flagd:** pin protobuf min version and remove it from renovate ([#1286](https://github.com/open-feature/java-sdk-contrib/issues/1286)) ([2d87b9c](https://github.com/open-feature/java-sdk-contrib/commit/2d87b9ca1eaa499329a9b482c9780164f68b33a6))


### 🧹 Chore

* **deps:** update dependency com.github.spotbugs:spotbugs-maven-plugin to v4.9.3.0 ([#1283](https://github.com/open-feature/java-sdk-contrib/issues/1283)) ([6af5e6d](https://github.com/open-feature/java-sdk-contrib/commit/6af5e6d5fa3076899c09be1cc585ffa3e063df74))
* **deps:** update providers/flagd/schemas digest to e840a03 ([#1272](https://github.com/open-feature/java-sdk-contrib/issues/1272)) ([0a2133c](https://github.com/open-feature/java-sdk-contrib/commit/0a2133c4fb43a428c6995fc0c5fa1f3bdbdc4c9c))
* **deps:** update providers/flagd/spec digest to aad6193 ([#1278](https://github.com/open-feature/java-sdk-contrib/issues/1278)) ([01b61fd](https://github.com/open-feature/java-sdk-contrib/commit/01b61fd4a76b861b1ab10dc342c30fe188b08dbc))
* log tweaks, retry cancels, add options.toBuidler ([#1276](https://github.com/open-feature/java-sdk-contrib/issues/1276)) ([fde9e39](https://github.com/open-feature/java-sdk-contrib/commit/fde9e3959b3870b87ef917049837e2062527c998))

## [0.11.7](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.11.6...dev.openfeature.contrib.providers.flagd-v0.11.7) (2025-03-12)


### 🐛 Bug Fixes

* transient error log-spam, add retry policy ([#1273](https://github.com/open-feature/java-sdk-contrib/issues/1273)) ([245e9ed](https://github.com/open-feature/java-sdk-contrib/commit/245e9edace570665df16a57a255aea8e6d34d5dd))

## [0.11.6](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.11.5...dev.openfeature.contrib.providers.flagd-v0.11.6) (2025-03-10)


### 🐛 Bug Fixes

* **deps:** update opentelemetry-java monorepo to v1.48.0 ([#1269](https://github.com/open-feature/java-sdk-contrib/issues/1269)) ([9958432](https://github.com/open-feature/java-sdk-contrib/commit/9958432fdef18a889b69b74a3ea4fcf550fa073d))


### 🧹 Chore

* adjust noisy log lines ([#1268](https://github.com/open-feature/java-sdk-contrib/issues/1268)) ([0a81a14](https://github.com/open-feature/java-sdk-contrib/commit/0a81a148e01e96f9bc24927a7c5075666f69d593))

## [0.11.5](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.11.4...dev.openfeature.contrib.providers.flagd-v0.11.5) (2025-03-06)


### 🐛 Bug Fixes

* broken reconnect on some HTTP2 error frames ([#1261](https://github.com/open-feature/java-sdk-contrib/issues/1261)) ([22d2a35](https://github.com/open-feature/java-sdk-contrib/commit/22d2a35cf7d0fd2bf212c442e5715c042041a737))
* **deps:** update grpc-java monorepo to v1.71.0 ([#1265](https://github.com/open-feature/java-sdk-contrib/issues/1265)) ([59569f3](https://github.com/open-feature/java-sdk-contrib/commit/59569f36fbc0d69844f34fd70e463e64a456b34d))
* **deps:** update jackson monorepo to v2.18.3 ([#1254](https://github.com/open-feature/java-sdk-contrib/issues/1254)) ([24f11fd](https://github.com/open-feature/java-sdk-contrib/commit/24f11fd5338e2693d1eda0654c4ea4bc29063742))


### ✨ New Features

* **flagd:** Add features to customize auth to Sync API server (authorityOverride and clientInterceptors) ([#1260](https://github.com/open-feature/java-sdk-contrib/issues/1260)) ([0c2803a](https://github.com/open-feature/java-sdk-contrib/commit/0c2803a8cf1e7285f84188410c3fe42b275d0624))
* **flagd:** Support supplying providerId for in-process resolver as an option ([#1259](https://github.com/open-feature/java-sdk-contrib/issues/1259)) ([5dbb073](https://github.com/open-feature/java-sdk-contrib/commit/5dbb073b6e76bfa7d2191c7ae5a15905b7e6d622))


### 🧹 Chore

* add disable metadata option ([#1267](https://github.com/open-feature/java-sdk-contrib/issues/1267)) ([28c65d5](https://github.com/open-feature/java-sdk-contrib/commit/28c65d57aec3695bf1e2c75cade78c73842999e5))
* **deps:** update dependency com.github.spotbugs:spotbugs-maven-plugin to v4.9.2.0 ([#1258](https://github.com/open-feature/java-sdk-contrib/issues/1258)) ([5976801](https://github.com/open-feature/java-sdk-contrib/commit/5976801198c7df5f9ca432364180c3f1eebcc818))
* **deps:** update dependency org.junit.jupiter:junit-jupiter to v5.12.0 ([#1248](https://github.com/open-feature/java-sdk-contrib/issues/1248)) ([31b1ebc](https://github.com/open-feature/java-sdk-contrib/commit/31b1ebcdf28df90fabe0e4fabe96b6ab5cff8300))
* **deps:** update providers/flagd/spec digest to 09aef37 ([#1266](https://github.com/open-feature/java-sdk-contrib/issues/1266)) ([d43fa00](https://github.com/open-feature/java-sdk-contrib/commit/d43fa0026983b2099905c742d5a06d6845e36db7))
* **deps:** update testcontainers-java monorepo to v1.20.6 ([#1263](https://github.com/open-feature/java-sdk-contrib/issues/1263)) ([4b85af4](https://github.com/open-feature/java-sdk-contrib/commit/4b85af43a201fd01938fa7b61469fa2e8bcfd626))

## [0.11.4](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.11.3...dev.openfeature.contrib.providers.flagd-v0.11.4) (2025-02-27)


### 🐛 Bug Fixes

* **deps:** update dependency com.google.protobuf:protobuf-java to v4 ([#1176](https://github.com/open-feature/java-sdk-contrib/issues/1176)) ([945f914](https://github.com/open-feature/java-sdk-contrib/commit/945f914531436195c8a5882507436e7a848e587c))
* **deps:** update dependency com.networknt:json-schema-validator to v1.5.6 ([#1238](https://github.com/open-feature/java-sdk-contrib/issues/1238)) ([3a37dfc](https://github.com/open-feature/java-sdk-contrib/commit/3a37dfc26b8a232d6f34e886f5ffe3599340e806))
* **deps:** update dependency io.netty:netty-transport-native-epoll to v4.1.119.final ([#1249](https://github.com/open-feature/java-sdk-contrib/issues/1249)) ([46f0c7b](https://github.com/open-feature/java-sdk-contrib/commit/46f0c7b26dd61826cbaa9cb3cb59be7b1fd3dde4))
* **deps:** update slf4j monorepo to v2.0.17 ([#1243](https://github.com/open-feature/java-sdk-contrib/issues/1243)) ([66c6a7f](https://github.com/open-feature/java-sdk-contrib/commit/66c6a7fc1bdc3e907793d2fc1eb0d412693a4aee))
* **flagd:** improve error messages for validation, if there are multiple errors ([#1250](https://github.com/open-feature/java-sdk-contrib/issues/1250)) ([82ca797](https://github.com/open-feature/java-sdk-contrib/commit/82ca797922731cd4451365c4aa99c30ea22072ea))
* RPC mode does not honor timeout ([#1230](https://github.com/open-feature/java-sdk-contrib/issues/1230)) ([5b509d0](https://github.com/open-feature/java-sdk-contrib/commit/5b509d01f08daed55b776960f1089023e25b30d3))


### ✨ New Features

* Improve wait logic to a more elegant solution [#1160](https://github.com/open-feature/java-sdk-contrib/issues/1160) ([#1169](https://github.com/open-feature/java-sdk-contrib/issues/1169)) ([4f484b7](https://github.com/open-feature/java-sdk-contrib/commit/4f484b72b23ff97b862726767d032dd5b94cf3a6))


### 🧹 Chore

* **deps:** update dependency com.github.spotbugs:spotbugs-maven-plugin to v4.9.1.0 ([#1241](https://github.com/open-feature/java-sdk-contrib/issues/1241)) ([9ef867d](https://github.com/open-feature/java-sdk-contrib/commit/9ef867dee4e4a33bb0a7f66316129f0622e8f687))
* **deps:** update dependency io.rest-assured:rest-assured to v5.5.1 ([#1224](https://github.com/open-feature/java-sdk-contrib/issues/1224)) ([1b8fa75](https://github.com/open-feature/java-sdk-contrib/commit/1b8fa75178baf7f0b87703efeadcaac208f26f01))
* **deps:** update providers/flagd/schemas digest to bb76343 ([#1204](https://github.com/open-feature/java-sdk-contrib/issues/1204)) ([69ccfef](https://github.com/open-feature/java-sdk-contrib/commit/69ccfef67f502a2d16ef0a383809f3589a2e4151))
* **deps:** update providers/flagd/spec digest to 0cd553d ([#1240](https://github.com/open-feature/java-sdk-contrib/issues/1240)) ([7419c69](https://github.com/open-feature/java-sdk-contrib/commit/7419c692e066c436f588d787beee7a2a0d0348a7))
* **deps:** update providers/flagd/spec digest to a69f748 ([#1229](https://github.com/open-feature/java-sdk-contrib/issues/1229)) ([1a884d5](https://github.com/open-feature/java-sdk-contrib/commit/1a884d50686156a3e93592683bf2bdbcb5db0ade))
* **deps:** update providers/flagd/spec digest to a69f748 ([#1235](https://github.com/open-feature/java-sdk-contrib/issues/1235)) ([1e72537](https://github.com/open-feature/java-sdk-contrib/commit/1e72537e046f2dbeff50d9967053c9fbe6d3b439))
* **deps:** update providers/flagd/test-harness digest to f5afee5 ([#1232](https://github.com/open-feature/java-sdk-contrib/issues/1232)) ([dad7648](https://github.com/open-feature/java-sdk-contrib/commit/dad7648a33c79362936ace209c9b061ba370998d))
* **deps:** update testcontainers-java monorepo to v1.20.5 ([#1237](https://github.com/open-feature/java-sdk-contrib/issues/1237)) ([99366df](https://github.com/open-feature/java-sdk-contrib/commit/99366df2c06117fc43a754758bcba71d1cebafb9))

## [0.11.3](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.11.2...dev.openfeature.contrib.providers.flagd-v0.11.3) (2025-02-18)


### 🐛 Bug Fixes

* enriched context lost on some events ([#1226](https://github.com/open-feature/java-sdk-contrib/issues/1226)) ([aefa941](https://github.com/open-feature/java-sdk-contrib/commit/aefa9410442baa5d801350e9b039ea27b9b8a41d))


### 🧹 Chore

* **deps:** update dependency providers/flagd/test-harness to v2.2.0 ([#1223](https://github.com/open-feature/java-sdk-contrib/issues/1223)) ([5f75991](https://github.com/open-feature/java-sdk-contrib/commit/5f75991a59a2bb098ef5b927ecdace61e1baf0fd))
* **deps:** update providers/flagd/spec digest to 54952f3 ([#1218](https://github.com/open-feature/java-sdk-contrib/issues/1218)) ([5a19a8d](https://github.com/open-feature/java-sdk-contrib/commit/5a19a8ddf881b9591b9416d4b859d8c656a63519))
* **deps:** update providers/flagd/test-harness digest to ec1d75c ([#1198](https://github.com/open-feature/java-sdk-contrib/issues/1198)) ([3fe0871](https://github.com/open-feature/java-sdk-contrib/commit/3fe0871d4765228983cc6da5de0bf8b2f66854a6))
* update testbed ([#1225](https://github.com/open-feature/java-sdk-contrib/issues/1225)) ([020c9a1](https://github.com/open-feature/java-sdk-contrib/commit/020c9a14fedddd3fec450a87db45dead0c33f4c2))

## [0.11.2](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.11.1...dev.openfeature.contrib.providers.flagd-v0.11.2) (2025-02-13)


### 🐛 Bug Fixes

* selector not being sent in sync call ([#1220](https://github.com/open-feature/java-sdk-contrib/issues/1220)) ([99e25ce](https://github.com/open-feature/java-sdk-contrib/commit/99e25cead7c501e62a454b0ff37fe971b9fd5b13))


### 🧹 Chore

* **flagd:** Improve grpc logging ([#1219](https://github.com/open-feature/java-sdk-contrib/issues/1219)) ([34f83c5](https://github.com/open-feature/java-sdk-contrib/commit/34f83c5933ab5f2df81af348036f9c403c7963af))

## [0.11.1](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.11.0...dev.openfeature.contrib.providers.flagd-v0.11.1) (2025-02-12)


### 🐛 Bug Fixes

* missing common lang dep ([#1216](https://github.com/open-feature/java-sdk-contrib/issues/1216)) ([379a89d](https://github.com/open-feature/java-sdk-contrib/commit/379a89d0563982dcb65ca060678d049e2e39f264))

## [0.11.0](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.10.5...dev.openfeature.contrib.providers.flagd-v0.11.0) (2025-02-12)


### ⚠ BREAKING CHANGES

* implement grpc reconnect for inprocess mode ([#1150](https://github.com/open-feature/java-sdk-contrib/issues/1150))
* Use grpc intern reconnections for  rpc event stream ([#1112](https://github.com/open-feature/java-sdk-contrib/issues/1112))

### 🐛 Bug Fixes

* **deps:** update dependency com.google.code.gson:gson to v2.12.0 ([#1184](https://github.com/open-feature/java-sdk-contrib/issues/1184)) ([40795a6](https://github.com/open-feature/java-sdk-contrib/commit/40795a6e52295e56f0604ea7a6c8fe61c1e1c04e))
* **deps:** update dependency com.google.code.gson:gson to v2.12.1 ([#1188](https://github.com/open-feature/java-sdk-contrib/issues/1188)) ([c24ef48](https://github.com/open-feature/java-sdk-contrib/commit/c24ef489910bf11a5be4b9f554d979fb739690b4))
* **deps:** update dependency com.google.protobuf:protobuf-java to v3.25.6 ([#1178](https://github.com/open-feature/java-sdk-contrib/issues/1178)) ([417c6df](https://github.com/open-feature/java-sdk-contrib/commit/417c6df6b7e47dd2cd7a335af4846e331e8b5cea))
* **deps:** update dependency com.networknt:json-schema-validator to v1.5.5 ([#1156](https://github.com/open-feature/java-sdk-contrib/issues/1156)) ([514004f](https://github.com/open-feature/java-sdk-contrib/commit/514004fa77180c12bf24c64aba83b3b37471f0c7))
* **deps:** update dependency commons-codec:commons-codec to v1.17.2 ([#1145](https://github.com/open-feature/java-sdk-contrib/issues/1145)) ([ee91441](https://github.com/open-feature/java-sdk-contrib/commit/ee91441b97b0622d54bc36d1be9be85f7f2372c6))
* **deps:** update dependency commons-codec:commons-codec to v1.18.0 ([#1181](https://github.com/open-feature/java-sdk-contrib/issues/1181)) ([d49d98f](https://github.com/open-feature/java-sdk-contrib/commit/d49d98fa573b677b4c34abf38e1e0e8d351aa0e2))
* **deps:** update dependency io.netty:netty-transport-native-epoll to v4.1.117.final ([#1155](https://github.com/open-feature/java-sdk-contrib/issues/1155)) ([5a293bb](https://github.com/open-feature/java-sdk-contrib/commit/5a293bbd2ee387d62f3f27e617e79e143d4e8b06))
* **deps:** update dependency io.netty:netty-transport-native-epoll to v4.1.118.final ([#1212](https://github.com/open-feature/java-sdk-contrib/issues/1212)) ([7e5ced2](https://github.com/open-feature/java-sdk-contrib/commit/7e5ced2c469e1286a8d21c5aba67eb5342a91831))
* **deps:** update dependency io.opentelemetry:opentelemetry-api to v1.46.0 ([#1151](https://github.com/open-feature/java-sdk-contrib/issues/1151)) ([8fb4194](https://github.com/open-feature/java-sdk-contrib/commit/8fb41945fe5c5a5d1ec0847ed3eaa595629b4362))
* **deps:** update dependency org.semver4j:semver4j to v5.6.0 ([#1171](https://github.com/open-feature/java-sdk-contrib/issues/1171)) ([23c5e69](https://github.com/open-feature/java-sdk-contrib/commit/23c5e6965426f6fc8fdbf6c9ee7941519402d774))
* **deps:** update grpc-java monorepo to v1.69.1 ([#1161](https://github.com/open-feature/java-sdk-contrib/issues/1161)) ([23db163](https://github.com/open-feature/java-sdk-contrib/commit/23db16318ef5d7cff3f6a701717addfef0d482cc))
* **deps:** update grpc-java monorepo to v1.70.0 ([#1172](https://github.com/open-feature/java-sdk-contrib/issues/1172)) ([ac751e8](https://github.com/open-feature/java-sdk-contrib/commit/ac751e8b7807eb39d6f74f767e18962b7bc69040))
* **deps:** update opentelemetry-java monorepo to v1.47.0 ([#1206](https://github.com/open-feature/java-sdk-contrib/issues/1206)) ([34cd441](https://github.com/open-feature/java-sdk-contrib/commit/34cd4411ba768a602e399f7f23a7b1b8fbdfd77b))
* rpc caching not behaving as expected (cleared too often) ([#1115](https://github.com/open-feature/java-sdk-contrib/issues/1115)) ([b4fe2f4](https://github.com/open-feature/java-sdk-contrib/commit/b4fe2f48ebb1368973d4f44c1a83d638b0e8b8b0))


### ✨ New Features

* **flagd:** migrate file to own provider type ([#1173](https://github.com/open-feature/java-sdk-contrib/issues/1173)) ([1bd8f86](https://github.com/open-feature/java-sdk-contrib/commit/1bd8f861755f998f0756684e69a5cf0ce6d7226a))
* implement grpc reconnect for inprocess mode ([#1150](https://github.com/open-feature/java-sdk-contrib/issues/1150)) ([d2410c7](https://github.com/open-feature/java-sdk-contrib/commit/d2410c70edcb59d3c5eedcff6071ca4963a096ac))
* Update in-process resolver to support flag metadata [#1102](https://github.com/open-feature/java-sdk-contrib/issues/1102) ([#1122](https://github.com/open-feature/java-sdk-contrib/issues/1122)) ([a330bd6](https://github.com/open-feature/java-sdk-contrib/commit/a330bd66aa50b85661feae8534e7e3def9287e5d))
* Use grpc intern reconnections for  rpc event stream ([#1112](https://github.com/open-feature/java-sdk-contrib/issues/1112)) ([d66adc9](https://github.com/open-feature/java-sdk-contrib/commit/d66adc914111c773dfbcfb78617a633b96f7f7c0))


### 🧹 Chore

* **deps:** update dependency providers/flagd/test-harness to v2 ([#1195](https://github.com/open-feature/java-sdk-contrib/issues/1195)) ([e1f2bc3](https://github.com/open-feature/java-sdk-contrib/commit/e1f2bc3dccd7734598b05baa4ec161a050ba4c5e))
* **deps:** update providers/flagd/schemas digest to 37baa2c ([#1142](https://github.com/open-feature/java-sdk-contrib/issues/1142)) ([d75e620](https://github.com/open-feature/java-sdk-contrib/commit/d75e62006a105f9f1a7e1d4e4d2d2c0f412ed6c6))
* **deps:** update providers/flagd/schemas digest to bb76343 ([#1180](https://github.com/open-feature/java-sdk-contrib/issues/1180)) ([142560f](https://github.com/open-feature/java-sdk-contrib/commit/142560f7a2ab77ba951321b5ea92deefe9ba3ee9))
* **deps:** update providers/flagd/schemas digest to bb76343 ([#1196](https://github.com/open-feature/java-sdk-contrib/issues/1196)) ([4fdb0a9](https://github.com/open-feature/java-sdk-contrib/commit/4fdb0a9d1d992a9d3d79e9a56942b20aa1142f85))
* **deps:** update providers/flagd/spec digest to 5b07065 ([#1179](https://github.com/open-feature/java-sdk-contrib/issues/1179)) ([63bb327](https://github.com/open-feature/java-sdk-contrib/commit/63bb3278002c90d1fa4ace2752e2fdce1025bc90))
* **deps:** update providers/flagd/spec digest to 6c673d7 ([#1157](https://github.com/open-feature/java-sdk-contrib/issues/1157)) ([cd0ea9e](https://github.com/open-feature/java-sdk-contrib/commit/cd0ea9e8d3d303b675b5d33287d94394e3c3aa6c))
* **deps:** update providers/flagd/spec digest to 8d6eeb3 ([#1194](https://github.com/open-feature/java-sdk-contrib/issues/1194)) ([d38e013](https://github.com/open-feature/java-sdk-contrib/commit/d38e013b83ac5bc0545a90a3c173a07c5788f460))
* **deps:** update providers/flagd/spec digest to 8d6eeb3 ([#1197](https://github.com/open-feature/java-sdk-contrib/issues/1197)) ([da76294](https://github.com/open-feature/java-sdk-contrib/commit/da76294aec6e3f07addc7a5f60bc862e127d439a))
* **deps:** update providers/flagd/spec digest to 95fe981 ([#1201](https://github.com/open-feature/java-sdk-contrib/issues/1201)) ([49b4218](https://github.com/open-feature/java-sdk-contrib/commit/49b4218af69fad8d639cab095ec94b5db72d769e))
* **deps:** update providers/flagd/spec digest to be56f22 ([#1210](https://github.com/open-feature/java-sdk-contrib/issues/1210)) ([5628cbc](https://github.com/open-feature/java-sdk-contrib/commit/5628cbc28d86ed6e221e7ca068d81106bcfc36cd))

## [0.10.5](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.10.2...dev.openfeature.contrib.providers.flagd-v0.10.5) (2025-01-03)


### ✨ New Features

* Improve e2e coverage ([#1092](https://github.com/open-feature/java-sdk-contrib/issues/1092)) ([d5110e3](https://github.com/open-feature/java-sdk-contrib/commit/d5110e3511eabdf9113e9bdb76a865e4036b624c))
* ssl e2e tests ([#1111](https://github.com/open-feature/java-sdk-contrib/issues/1111)) ([819abe3](https://github.com/open-feature/java-sdk-contrib/commit/819abe31898adc90dd0bbd3dde191f43afd8cb73))
* protobuf-java@4 compatibility ([#1125](https://github.com/open-feature/java-sdk-contrib/issues/1125)) ([e535976](https://github.com/open-feature/java-sdk-contrib/commit/e535976bf0b3fcb76519866b6f657d69fe85910a))
* chore: relax protobuf-java version req ([#1135](https://github.com/open-feature/java-sdk-contrib/issues/1135))

### 🧹 Chore

* **deps:** update providers/flagd/schemas digest to b81a56e ([#1117](https://github.com/open-feature/java-sdk-contrib/issues/1117)) ([828bd2c](https://github.com/open-feature/java-sdk-contrib/commit/828bd2ceb4b8ac2c220ab57c7186cffa040990a0))
* **deps:** update providers/flagd/spec digest to d261f68 ([#1123](https://github.com/open-feature/java-sdk-contrib/issues/1123)) ([4fa4ba2](https://github.com/open-feature/java-sdk-contrib/commit/4fa4ba279716365c817e8f06c9e6f4a9e1578cc0))
* **deps:** update providers/flagd/spec digest to ed0f9ef ([#1118](https://github.com/open-feature/java-sdk-contrib/issues/1118)) ([bb9767e](https://github.com/open-feature/java-sdk-contrib/commit/bb9767ede57627ecb2901707ba275bfe44fdcb08))
* **deps:** update testcontainers-java monorepo to v1.20.4 ([#1076](https://github.com/open-feature/java-sdk-contrib/issues/1076)) ([0bf3b83](https://github.com/open-feature/java-sdk-contrib/commit/0bf3b83a0d4966ae0ab8cf308d16934487c2cce1))

## [0.10.2](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.10.1...dev.openfeature.contrib.providers.flagd-v0.10.2) (2024-11-20)


### 🧹 Chore

* pin protobuf-java ([#1074](https://github.com/open-feature/java-sdk-contrib/issues/1074)) ([f29969e](https://github.com/open-feature/java-sdk-contrib/commit/f29969e215914535f6733c45bcb9b22e490d02c6))

## [0.10.1](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.10.0...dev.openfeature.contrib.providers.flagd-v0.10.1) (2024-11-18)


### 🐛 Bug Fixes

* **deps:** update dependency com.networknt:json-schema-validator to v1.5.3 ([#1052](https://github.com/open-feature/java-sdk-contrib/issues/1052)) ([a720f41](https://github.com/open-feature/java-sdk-contrib/commit/a720f4105b9da8bfd58307b38144b0d6000375ad))
* **deps:** update dependency io.netty:netty-transport-native-epoll to v4.1.115.final ([#1071](https://github.com/open-feature/java-sdk-contrib/issues/1071)) ([6e311dc](https://github.com/open-feature/java-sdk-contrib/commit/6e311dc99e5cc2e51729d5086437c706de19b9a5))
* **deps:** update dependency io.opentelemetry:opentelemetry-api to v1.44.0 ([#1069](https://github.com/open-feature/java-sdk-contrib/issues/1069)) ([2cd0489](https://github.com/open-feature/java-sdk-contrib/commit/2cd0489e61aecd7a8f87043cbd4382aaf6485f77))
* **deps:** update opentelemetry-java monorepo to v1.44.1 ([#1070](https://github.com/open-feature/java-sdk-contrib/issues/1070)) ([4d5fe86](https://github.com/open-feature/java-sdk-contrib/commit/4d5fe86a488a8781ab7efefb4a5958c4fae01c17))
* remove pinned protobuf version ([#1067](https://github.com/open-feature/java-sdk-contrib/issues/1067)) ([c8531e3](https://github.com/open-feature/java-sdk-contrib/commit/c8531e3dfabac49b8b492939bffb4f7da27e4f4c))

## [0.10.0](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.9.3...dev.openfeature.contrib.providers.flagd-v0.10.0) (2024-10-29)


### ⚠ BREAKING CHANGES

* change FLAGD_GRPC_TARGET env to FLAGD_TARGET_URI ([#1050](https://github.com/open-feature/java-sdk-contrib/issues/1050))

### 🐛 Bug Fixes

* change FLAGD_GRPC_TARGET env to FLAGD_TARGET_URI ([#1050](https://github.com/open-feature/java-sdk-contrib/issues/1050)) ([521f776](https://github.com/open-feature/java-sdk-contrib/commit/521f776ad1abbc6de1ee94f056e84659e8665243))
* **deps:** update dependency com.fasterxml.jackson.core:jackson-databind to v2.18.1 ([#1047](https://github.com/open-feature/java-sdk-contrib/issues/1047)) ([a2ee3e6](https://github.com/open-feature/java-sdk-contrib/commit/a2ee3e6ed0c15c3ebaf55adc10198760f51a4a30))
* **deps:** update grpc-java monorepo to v1.68.1 ([#1049](https://github.com/open-feature/java-sdk-contrib/issues/1049)) ([da41a95](https://github.com/open-feature/java-sdk-contrib/commit/da41a950c291ace0b9fbc82a3ae1867ce2fe4e82))


### 🧹 Chore

* **deps:** update dependency org.codehaus.mojo:exec-maven-plugin to v3.5.0 ([#1034](https://github.com/open-feature/java-sdk-contrib/issues/1034)) ([e377b74](https://github.com/open-feature/java-sdk-contrib/commit/e377b749f41e30975d0dcf3030a72fe0c8598382))
* **deps:** update testcontainers-java monorepo to v1.20.3 ([#1037](https://github.com/open-feature/java-sdk-contrib/issues/1037)) ([373381d](https://github.com/open-feature/java-sdk-contrib/commit/373381d74d38d15d49c56d9395945a30750cd746))

## [0.9.3](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.9.2...dev.openfeature.contrib.providers.flagd-v0.9.3) (2024-10-21)


### 🐛 Bug Fixes

* **deps:** update dependency org.semver4j:semver4j to v5.4.1 ([#1025](https://github.com/open-feature/java-sdk-contrib/issues/1025)) ([c58af09](https://github.com/open-feature/java-sdk-contrib/commit/c58af09d35d60bb13bdc4395b7bf9a8017e48344))
* protobuf-java version for CVE-2024-7254 ([#1030](https://github.com/open-feature/java-sdk-contrib/issues/1030)) ([1c3633c](https://github.com/open-feature/java-sdk-contrib/commit/1c3633c817bff2eaac33eb32ec28393f7df768c8))


### 🧹 Chore

* **deps:** update junit5 monorepo ([#1029](https://github.com/open-feature/java-sdk-contrib/issues/1029)) ([39f0c22](https://github.com/open-feature/java-sdk-contrib/commit/39f0c22e9a77f66d379848cee5dc2d5a3d711df9))

## [0.9.2](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.9.1...dev.openfeature.contrib.providers.flagd-v0.9.2) (2024-10-16)


### ✨ New Features

* added custom grpc resolver ([#1008](https://github.com/open-feature/java-sdk-contrib/issues/1008)) ([85403b7](https://github.com/open-feature/java-sdk-contrib/commit/85403b728e76c371049fec56a3096118f212250b))

## [0.9.1](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.9.0...dev.openfeature.contrib.providers.flagd-v0.9.1) (2024-10-15)


### 🐛 Bug Fixes

* **deps:** update opentelemetry-java monorepo to v1.43.0 ([#1020](https://github.com/open-feature/java-sdk-contrib/issues/1020)) ([67682a8](https://github.com/open-feature/java-sdk-contrib/commit/67682a8d8a0a69c157e0d4a78bf541c5c7c60971))
* make flagd config EvaluatorType public ([#1014](https://github.com/open-feature/java-sdk-contrib/issues/1014)) ([c99c66b](https://github.com/open-feature/java-sdk-contrib/commit/c99c66b3e6be4b61005f025f4ce355358dd116e6))


### ✨ New Features

* tolerate immediately recoverable stream faults, improve logging ([#1019](https://github.com/open-feature/java-sdk-contrib/issues/1019)) ([3110076](https://github.com/open-feature/java-sdk-contrib/commit/3110076474f9141473c23a4e5207a005fc619904))


### 🧹 Chore

* **deps:** update junit5 monorepo ([#1000](https://github.com/open-feature/java-sdk-contrib/issues/1000)) ([80c237e](https://github.com/open-feature/java-sdk-contrib/commit/80c237e91633617d88d6b42d3bcce8fa0492aec3))


### 📚 Documentation

* **flagd:** Update deadline docs ([#1011](https://github.com/open-feature/java-sdk-contrib/issues/1011)) ([40ed928](https://github.com/open-feature/java-sdk-contrib/commit/40ed92828b5fc959b8f0bd7ee777656980cf2179))

## [0.9.0](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.8.9...dev.openfeature.contrib.providers.flagd-v0.9.0) (2024-10-04)


### ⚠ BREAKING CHANGES

* context enrichment via contextEnricher, not from init ([#991](https://github.com/open-feature/java-sdk-contrib/issues/991))
* use sdk-maintained state, require 1.12 ([#964](https://github.com/open-feature/java-sdk-contrib/issues/964))

### 🐛 Bug Fixes

* **deps:** update dependency com.fasterxml.jackson.core:jackson-databind to v2.18.0 ([#979](https://github.com/open-feature/java-sdk-contrib/issues/979)) ([7e1a13e](https://github.com/open-feature/java-sdk-contrib/commit/7e1a13ec79b82f8fa49703af58087fea1874cea5))
* **deps:** update dependency com.networknt:json-schema-validator to v1.5.2 ([#958](https://github.com/open-feature/java-sdk-contrib/issues/958)) ([da10fe8](https://github.com/open-feature/java-sdk-contrib/commit/da10fe856b53ac3bdf284a194e011895f397bec3))
* **deps:** update dependency io.netty:netty-transport-native-epoll to v4.1.114.final ([#994](https://github.com/open-feature/java-sdk-contrib/issues/994)) ([3e9b967](https://github.com/open-feature/java-sdk-contrib/commit/3e9b967525a0bddb76f5ebb5b2c70ae92b038a42))
* **deps:** update grpc-java monorepo to v1.68.0 ([#962](https://github.com/open-feature/java-sdk-contrib/issues/962)) ([96a78bd](https://github.com/open-feature/java-sdk-contrib/commit/96a78bdf3a01445eb41d4496f51660666281668a))


### ✨ New Features

* Add GRPC stream connection deadline ([#999](https://github.com/open-feature/java-sdk-contrib/issues/999)) ([9de03df](https://github.com/open-feature/java-sdk-contrib/commit/9de03df3f3d533fa8ca243e65fb86c6abc460252))
* context enrichment via contextEnricher, not from init ([#991](https://github.com/open-feature/java-sdk-contrib/issues/991)) ([1c2e11b](https://github.com/open-feature/java-sdk-contrib/commit/1c2e11baa2222e236abd96adb8274b93a58a93cd))
* expose sync-metadata, call RPC with (re)connect ([#967](https://github.com/open-feature/java-sdk-contrib/issues/967)) ([61bb726](https://github.com/open-feature/java-sdk-contrib/commit/61bb7263a8b4ad2b15560b1306b04cd28986a95f))
* use sdk-maintained state, require 1.12 ([#964](https://github.com/open-feature/java-sdk-contrib/issues/964)) ([4a041b0](https://github.com/open-feature/java-sdk-contrib/commit/4a041b0dda9c4e460f4c2199f3bc680df0dda621))


### 🧹 Chore

* **deps:** update junit5 monorepo ([#970](https://github.com/open-feature/java-sdk-contrib/issues/970)) ([df66295](https://github.com/open-feature/java-sdk-contrib/commit/df662955809698650428303d811c2b3a4b135463))
* **deps:** update testcontainers-java monorepo to v1.20.2 ([#992](https://github.com/open-feature/java-sdk-contrib/issues/992)) ([aba1ae3](https://github.com/open-feature/java-sdk-contrib/commit/aba1ae31ebe26137904218c6737ebe625d267f4e))

## [0.8.9](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.8.8...dev.openfeature.contrib.providers.flagd-v0.8.9) (2024-09-17)


### 🐛 Bug Fixes

* ConcurrentModificationException on flag config change java 9 ([#954](https://github.com/open-feature/java-sdk-contrib/issues/954)) ([f74fe5f](https://github.com/open-feature/java-sdk-contrib/commit/f74fe5f6da44a7fdaaaa60efa21d4ddbb3b00ec7))
* **deps:** update dependency org.semver4j:semver4j to v5.4.0 ([#952](https://github.com/open-feature/java-sdk-contrib/issues/952)) ([61c4f2a](https://github.com/open-feature/java-sdk-contrib/commit/61c4f2a8aa66b00435e34c2c97f0855edee654f2))
* **deps:** update opentelemetry-java monorepo to v1.42.1 ([#946](https://github.com/open-feature/java-sdk-contrib/issues/946)) ([0ca3da6](https://github.com/open-feature/java-sdk-contrib/commit/0ca3da649cbeb03039f7ea79134475093b739143))


### ✨ New Features

* emit changed flags in configuration change event ([#925](https://github.com/open-feature/java-sdk-contrib/issues/925)) ([d3de874](https://github.com/open-feature/java-sdk-contrib/commit/d3de8746941ff74e51e0a21675aded2bf799dc4e))
* flow instead of exceptions in resolver ([#942](https://github.com/open-feature/java-sdk-contrib/issues/942)) ([03dfc91](https://github.com/open-feature/java-sdk-contrib/commit/03dfc91ae5bb47de90129abefc1146daa496ea17))

## [0.8.8](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.8.7...dev.openfeature.contrib.providers.flagd-v0.8.8) (2024-09-10)


### 🐛 Bug Fixes

* **deps:** update dependency io.netty:netty-transport-native-epoll to v4.1.113.final ([#936](https://github.com/open-feature/java-sdk-contrib/issues/936)) ([6686300](https://github.com/open-feature/java-sdk-contrib/commit/6686300b62bd7b51283c6131bb24174bb2bb331f))
* **deps:** update opentelemetry-java monorepo to v1.42.0 ([#939](https://github.com/open-feature/java-sdk-contrib/issues/939)) ([67e855c](https://github.com/open-feature/java-sdk-contrib/commit/67e855c0eeeb8f36cd6ebb901deb3bfa3ea90695))
* use keepalive for TCP & use unit in env variable name ([#945](https://github.com/open-feature/java-sdk-contrib/issues/945)) ([d615499](https://github.com/open-feature/java-sdk-contrib/commit/d615499b7f213983da10c2fb9269cf47340f7110))


### 🧹 Chore

* add env keepalive test ([#943](https://github.com/open-feature/java-sdk-contrib/issues/943)) ([b07248c](https://github.com/open-feature/java-sdk-contrib/commit/b07248cf5843fea67775b31fa60b7f11e2e75919))

## [0.8.7](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.8.6...dev.openfeature.contrib.providers.flagd-v0.8.7) (2024-08-29)


### ✨ New Features

* add gRPC keepalive ([#930](https://github.com/open-feature/java-sdk-contrib/issues/930)) ([6833433](https://github.com/open-feature/java-sdk-contrib/commit/6833433fcd2334c3df0cb30efedaba2f2984062d))

## [0.8.6](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.8.5...dev.openfeature.contrib.providers.flagd-v0.8.6) (2024-08-27)


### 🐛 Bug Fixes

* **deps:** update grpc-java monorepo to v1.66.0 ([#909](https://github.com/open-feature/java-sdk-contrib/issues/909)) ([a1bd2db](https://github.com/open-feature/java-sdk-contrib/commit/a1bd2db5e91242b9ef23651c8a874ce6ef7a4782))
* **deps:** update opentelemetry-java monorepo to v1.41.0 ([#911](https://github.com/open-feature/java-sdk-contrib/issues/911)) ([157705a](https://github.com/open-feature/java-sdk-contrib/commit/157705a44e55f5b5a545f9126b64df00b1f8cdf9))


### 🧹 Chore

* add more logging in sync stream ([#929](https://github.com/open-feature/java-sdk-contrib/issues/929)) ([64c9f13](https://github.com/open-feature/java-sdk-contrib/commit/64c9f13a4afeae13df9ddd32dc2e942f76f9d905))
* Create docker-compose.yml do match CONTRIBUTING.md ([#918](https://github.com/open-feature/java-sdk-contrib/issues/918)) ([d81702e](https://github.com/open-feature/java-sdk-contrib/commit/d81702ed0414dc6654d957663f4441fbf85ab6cb))
* **deps:** update dependency org.codehaus.mojo:exec-maven-plugin to v3.4.0 ([#905](https://github.com/open-feature/java-sdk-contrib/issues/905)) ([fe213ee](https://github.com/open-feature/java-sdk-contrib/commit/fe213ee895140952057746b909115c7110811ef8))
* **deps:** update dependency org.codehaus.mojo:exec-maven-plugin to v3.4.1 ([#914](https://github.com/open-feature/java-sdk-contrib/issues/914)) ([c68d0c5](https://github.com/open-feature/java-sdk-contrib/commit/c68d0c5cca31582a6952cb0054acec8ca195a716))
* **deps:** update junit5 monorepo ([#917](https://github.com/open-feature/java-sdk-contrib/issues/917)) ([0fe925a](https://github.com/open-feature/java-sdk-contrib/commit/0fe925ae4584527c20be64dfc2e1af491df766cd))

## [0.8.5](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.8.4...dev.openfeature.contrib.providers.flagd-v0.8.5) (2024-08-06)


### 🐛 Bug Fixes

* **deps:** update dependency com.networknt:json-schema-validator to v1.5.1 ([#891](https://github.com/open-feature/java-sdk-contrib/issues/891)) ([353f77b](https://github.com/open-feature/java-sdk-contrib/commit/353f77b24349a90e34b1823a60464e6369995c87))
* **deps:** update dependency commons-codec:commons-codec to v1.17.1 ([#881](https://github.com/open-feature/java-sdk-contrib/issues/881)) ([ee8273e](https://github.com/open-feature/java-sdk-contrib/commit/ee8273e3bcea0b2d0dcfa6d58bd68b68edb551e6))
* **deps:** update dependency io.netty:netty-transport-native-epoll to v4.1.112.final ([#886](https://github.com/open-feature/java-sdk-contrib/issues/886)) ([1c6c890](https://github.com/open-feature/java-sdk-contrib/commit/1c6c890a83fec49394a3a44c82ef75f80613e586))
* **deps:** update grpc-java monorepo to v1.65.1 ([#878](https://github.com/open-feature/java-sdk-contrib/issues/878)) ([d307cc2](https://github.com/open-feature/java-sdk-contrib/commit/d307cc25ff042c084529655f4ebd953d2fa91b5c))


### ✨ New Features

* [flagd-in-process] Support Injection of a custom connector ([#900](https://github.com/open-feature/java-sdk-contrib/issues/900)) ([b9f9ffd](https://github.com/open-feature/java-sdk-contrib/commit/b9f9ffddf7538bbd8f7ffb531752c9468dbb87b1))


### 🧹 Chore

* **deps:** update testcontainers-java monorepo to v1.20.0 ([#882](https://github.com/open-feature/java-sdk-contrib/issues/882)) ([2861e4b](https://github.com/open-feature/java-sdk-contrib/commit/2861e4b5195c3a626bced6b393e714e21c8144e9))
* **deps:** update testcontainers-java monorepo to v1.20.1 ([#897](https://github.com/open-feature/java-sdk-contrib/issues/897)) ([6f76193](https://github.com/open-feature/java-sdk-contrib/commit/6f76193bf34c3230945a6c0bb8cc0f818664af5b))

## [0.8.4](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.8.3...dev.openfeature.contrib.providers.flagd-v0.8.4) (2024-07-08)


### 🐛 Bug Fixes

* **deps:** update dependency com.fasterxml.jackson.core:jackson-databind to v2.17.2 ([#866](https://github.com/open-feature/java-sdk-contrib/issues/866)) ([cf66811](https://github.com/open-feature/java-sdk-contrib/commit/cf668118351120b8a86b08f30facb38f7ec51086))
* **deps:** update dependency com.networknt:json-schema-validator to v1.4.2 ([#842](https://github.com/open-feature/java-sdk-contrib/issues/842)) ([d39dd8b](https://github.com/open-feature/java-sdk-contrib/commit/d39dd8b084cd2f360eafa726e5828a755d12874c))
* **deps:** update dependency com.networknt:json-schema-validator to v1.4.3 ([#845](https://github.com/open-feature/java-sdk-contrib/issues/845)) ([301f852](https://github.com/open-feature/java-sdk-contrib/commit/301f85230aa50eef1b35f039fac862d86eb72453))
* **deps:** update dependency com.networknt:json-schema-validator to v1.5.0 ([#869](https://github.com/open-feature/java-sdk-contrib/issues/869)) ([05a7611](https://github.com/open-feature/java-sdk-contrib/commit/05a7611c6324e0d0a709995050fe8f77435632d7))
* **deps:** update grpc-java monorepo to v1.65.0 ([#849](https://github.com/open-feature/java-sdk-contrib/issues/849)) ([50ff3b8](https://github.com/open-feature/java-sdk-contrib/commit/50ff3b8f8a0557f37d8c2d619ea31cf642e9dd7e))
* **deps:** update opentelemetry-java monorepo to v1.40.0 ([#870](https://github.com/open-feature/java-sdk-contrib/issues/870)) ([53f4435](https://github.com/open-feature/java-sdk-contrib/commit/53f4435dce0b0f80fcae48d3f664a88d9734e7d0))


### ✨ New Features

* Change fractional custom op from percentage-based to relative weighting. [#828](https://github.com/open-feature/java-sdk-contrib/issues/828) ([#833](https://github.com/open-feature/java-sdk-contrib/issues/833)) ([2e5c146](https://github.com/open-feature/java-sdk-contrib/commit/2e5c1468b6fb76391d59cb64bd3b8ced48a60977))
* **flagd:** testcontainers instead of docker compose ([#860](https://github.com/open-feature/java-sdk-contrib/issues/860)) ([5086f18](https://github.com/open-feature/java-sdk-contrib/commit/5086f18ca943f790fcd84cf65059c2ae56ebbd12))
* Reset the state on shutting down the flagd resolver ([#410](https://github.com/open-feature/java-sdk-contrib/issues/410)) ([#832](https://github.com/open-feature/java-sdk-contrib/issues/832)) ([05ea93d](https://github.com/open-feature/java-sdk-contrib/commit/05ea93d713d2b8c85d2f1e08f598fe18554789a5))
* use namespaced schemas for flagd json schemas ([#843](https://github.com/open-feature/java-sdk-contrib/issues/843)) ([#850](https://github.com/open-feature/java-sdk-contrib/issues/850)) ([efc3a9e](https://github.com/open-feature/java-sdk-contrib/commit/efc3a9eb88a5e02d181bf3dd38331648122af56b))


### 🧹 Chore

* **deps:** update dependency org.junit.jupiter:junit-jupiter to v5.10.3 ([#861](https://github.com/open-feature/java-sdk-contrib/issues/861)) ([4cf8d47](https://github.com/open-feature/java-sdk-contrib/commit/4cf8d47b60b7d233d42dd550d52290fc909b9830))
* **deps:** update ghcr.io/open-feature/flagd-testbed docker tag to v0.5.5 ([#851](https://github.com/open-feature/java-sdk-contrib/issues/851)) ([07841e8](https://github.com/open-feature/java-sdk-contrib/commit/07841e8fb42dff15d9fe7dc53e08b51cbc11aff3))
* **deps:** update ghcr.io/open-feature/flagd-testbed-unstable docker tag to v0.5.5 ([#852](https://github.com/open-feature/java-sdk-contrib/issues/852)) ([021ddb6](https://github.com/open-feature/java-sdk-contrib/commit/021ddb67ec8122a3609f10c6664e3920f00277cb))
* **deps:** update ghcr.io/open-feature/sync-testbed docker tag to v0.5.5 ([#853](https://github.com/open-feature/java-sdk-contrib/issues/853)) ([4f12954](https://github.com/open-feature/java-sdk-contrib/commit/4f129545654368e64e884bd0e4ef48bba6d01e2f))
* **deps:** update ghcr.io/open-feature/sync-testbed-unstable docker tag to v0.5.5 ([#854](https://github.com/open-feature/java-sdk-contrib/issues/854)) ([ba4f7f7](https://github.com/open-feature/java-sdk-contrib/commit/ba4f7f7c5de0285c6255e3c265ed325365e98575))
* fix pmd violations ([#856](https://github.com/open-feature/java-sdk-contrib/issues/856)) ([f10d872](https://github.com/open-feature/java-sdk-contrib/commit/f10d87205dd6a21222de362694d208fd293d9200))
* fractional shorthand tests ([#862](https://github.com/open-feature/java-sdk-contrib/issues/862)) ([dccea53](https://github.com/open-feature/java-sdk-contrib/commit/dccea53dfb2075f01e2307e5312e34019a45e7a4))
* update flagd json submodule ([#874](https://github.com/open-feature/java-sdk-contrib/issues/874)) ([d8a7a0a](https://github.com/open-feature/java-sdk-contrib/commit/d8a7a0a544393a893312ac067d4ec79d59357046))

## [0.8.3](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.8.2...dev.openfeature.contrib.providers.flagd-v0.8.3) (2024-06-19)


### 🐛 Bug Fixes

* broken netty transport ([#834](https://github.com/open-feature/java-sdk-contrib/issues/834)) ([92a0499](https://github.com/open-feature/java-sdk-contrib/commit/92a049933e9efd756b59db44487597b66878ff6e))
* **deps:** update dependency io.netty:netty-transport-native-epoll to v4.1.111.final ([#819](https://github.com/open-feature/java-sdk-contrib/issues/819)) ([6bc7761](https://github.com/open-feature/java-sdk-contrib/commit/6bc7761672c8c488933323e19cc6f80801841f8f))
* update flagd schema to latest tag ([#836](https://github.com/open-feature/java-sdk-contrib/issues/836)) ([732f567](https://github.com/open-feature/java-sdk-contrib/commit/732f567f583c3c35bb089c6e7ac9a37a1bb8a7f6))

## [0.8.2](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.8.1...dev.openfeature.contrib.providers.flagd-v0.8.2) (2024-06-14)


### 🐛 Bug Fixes

* **deps:** update dependency com.google.code.gson:gson to v2.11.0 ([#794](https://github.com/open-feature/java-sdk-contrib/issues/794)) ([e6ce0ea](https://github.com/open-feature/java-sdk-contrib/commit/e6ce0ea307b07d7813fc65b57737feb8beb571bb))
* **deps:** update dependency io.netty:netty-transport-native-epoll to v4.1.110.final ([#798](https://github.com/open-feature/java-sdk-contrib/issues/798)) ([f7333ec](https://github.com/open-feature/java-sdk-contrib/commit/f7333ecddfceba342b175c9ca3ee1846ab61e7c6))
* **deps:** update opentelemetry-java monorepo to v1.39.0 ([#813](https://github.com/open-feature/java-sdk-contrib/issues/813)) ([cbf4232](https://github.com/open-feature/java-sdk-contrib/commit/cbf42324351f25889e59bf8b29d5ed4752ab3c98))


### ✨ New Features

* [flagd] Default port to 8015 if in-process resolver is used. ([#810](https://github.com/open-feature/java-sdk-contrib/issues/810)) ([9b7dc9a](https://github.com/open-feature/java-sdk-contrib/commit/9b7dc9a71cb8060bce0112dce4c7650f9f3aa6c9))
* add JUnit Pioneer as testing dependency ([#820](https://github.com/open-feature/java-sdk-contrib/issues/820)) ([3a9c916](https://github.com/open-feature/java-sdk-contrib/commit/3a9c9165185ddfdfccdd997b81c2e8ff2be63b56))
* flagd support resolver type from env vars ([#792](https://github.com/open-feature/java-sdk-contrib/issues/792)) ([49d47b8](https://github.com/open-feature/java-sdk-contrib/commit/49d47b8bc62d150d45630e6e0fc625724a1d7b62))
* introduce Resolver as a drop in replacement for Evaluator ([#793](https://github.com/open-feature/java-sdk-contrib/issues/793)) ([618a64a](https://github.com/open-feature/java-sdk-contrib/commit/618a64a93dbc1108e8382c4505324f7671ffdb04))


### 🧹 Chore

* **deps:** update dependency org.codehaus.mojo:exec-maven-plugin to v3.3.0 ([#797](https://github.com/open-feature/java-sdk-contrib/issues/797)) ([fbe818b](https://github.com/open-feature/java-sdk-contrib/commit/fbe818b3946e3985188593934023c696dfb7afb9))

## [0.8.1](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.8.0...dev.openfeature.contrib.providers.flagd-v0.8.1) (2024-05-17)


### 🐛 Bug Fixes

* **deps:** update dependency com.fasterxml.jackson.core:jackson-databind to v2.17.1 ([#777](https://github.com/open-feature/java-sdk-contrib/issues/777)) ([8b582d6](https://github.com/open-feature/java-sdk-contrib/commit/8b582d6052fd22b8141a9765b2a1a261933fd3a2))
* **deps:** update dependency commons-codec:commons-codec to v1.17.0 ([#769](https://github.com/open-feature/java-sdk-contrib/issues/769)) ([3fbb213](https://github.com/open-feature/java-sdk-contrib/commit/3fbb2137575d716e3f2e62c6c49f860aa748ce39))
* **deps:** update dependency io.netty:netty-transport-native-epoll to v4.1.109.final ([#756](https://github.com/open-feature/java-sdk-contrib/issues/756)) ([765cb36](https://github.com/open-feature/java-sdk-contrib/commit/765cb36b7413c765cf90c0805c6db2e7281909bd))
* **deps:** update dependency org.semver4j:semver4j to v5.3.0 ([#767](https://github.com/open-feature/java-sdk-contrib/issues/767)) ([c43fe00](https://github.com/open-feature/java-sdk-contrib/commit/c43fe00277c297516f3e83fefdb05a9a8794bce9))
* **deps:** update grpc-java monorepo to v1.64.0 ([#788](https://github.com/open-feature/java-sdk-contrib/issues/788)) ([03a545a](https://github.com/open-feature/java-sdk-contrib/commit/03a545a742ecfc89a74bcaf12bd93de2fa0c1fed))
* **deps:** update opentelemetry-java monorepo to v1.38.0 ([#785](https://github.com/open-feature/java-sdk-contrib/issues/785)) ([61ac99f](https://github.com/open-feature/java-sdk-contrib/commit/61ac99f577b08b8abcfcaccb421d950465907f2c))
* update flagd schema to remove warning ([#789](https://github.com/open-feature/java-sdk-contrib/issues/789)) ([77e9528](https://github.com/open-feature/java-sdk-contrib/commit/77e9528573cef8daf5127fce125522e254b2d1ed))

## [0.8.0](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.7.0...dev.openfeature.contrib.providers.flagd-v0.8.0) (2024-04-11)


### ⚠ BREAKING CHANGES

* allow overrides for fractional seed ([#737](https://github.com/open-feature/java-sdk-contrib/issues/737))

### 🐛 Bug Fixes

* **deps:** update dependency com.fasterxml.jackson.core:jackson-databind to v2.16.2 ([#707](https://github.com/open-feature/java-sdk-contrib/issues/707)) ([2ce424d](https://github.com/open-feature/java-sdk-contrib/commit/2ce424dd780a04c49efe29093a33bd26d0ceccc5))
* **deps:** update dependency com.fasterxml.jackson.core:jackson-databind to v2.17.0 ([#714](https://github.com/open-feature/java-sdk-contrib/issues/714)) ([a5964f0](https://github.com/open-feature/java-sdk-contrib/commit/a5964f0654124b668e50a5df7cf82c1028457f95))
* **deps:** update dependency com.networknt:json-schema-validator to v1.4.0 ([#721](https://github.com/open-feature/java-sdk-contrib/issues/721)) ([862a0f2](https://github.com/open-feature/java-sdk-contrib/commit/862a0f2c192e525cfef47ec275f479c9c19a5ade))
* **deps:** update dependency io.netty:netty-transport-native-epoll to v4.1.108.final ([#729](https://github.com/open-feature/java-sdk-contrib/issues/729)) ([f03fa26](https://github.com/open-feature/java-sdk-contrib/commit/f03fa2635be9fbf3b6b3bc9641441b5d593ca2d2))
* **deps:** update dependency io.opentelemetry:opentelemetry-api to v1.37.0 ([#748](https://github.com/open-feature/java-sdk-contrib/issues/748)) ([e94df12](https://github.com/open-feature/java-sdk-contrib/commit/e94df12b1a6d3f4a26cfa3aa6c7aac7551e4fe53))
* **deps:** update dependency org.semver4j:semver4j to v5.2.3 ([#740](https://github.com/open-feature/java-sdk-contrib/issues/740)) ([387f8ae](https://github.com/open-feature/java-sdk-contrib/commit/387f8aedec25141f581026a46abf40c70ceacaa3))
* **deps:** update grpc-java monorepo to v1.62.2 ([#695](https://github.com/open-feature/java-sdk-contrib/issues/695)) ([97da222](https://github.com/open-feature/java-sdk-contrib/commit/97da2226c15b61b8835a56e511420869cc961d17))
* **deps:** update grpc-java monorepo to v1.63.0 ([#739](https://github.com/open-feature/java-sdk-contrib/issues/739)) ([2d7b262](https://github.com/open-feature/java-sdk-contrib/commit/2d7b26217a2ee4a960806e9d226f0909f24c2d5d))
* **deps:** update opentelemetry-java monorepo to v1.36.0 ([#703](https://github.com/open-feature/java-sdk-contrib/issues/703)) ([712b48c](https://github.com/open-feature/java-sdk-contrib/commit/712b48c8a25dc8733c03ea862f499e1a60271e13))
* potential finalizer attack ([#702](https://github.com/open-feature/java-sdk-contrib/issues/702)) ([572df60](https://github.com/open-feature/java-sdk-contrib/commit/572df60e3d4ef2d6039a8b2cd8554423179ffc30))


### ✨ New Features

* allow overrides for fractional seed ([#737](https://github.com/open-feature/java-sdk-contrib/issues/737)) ([ab6b888](https://github.com/open-feature/java-sdk-contrib/commit/ab6b8880ae97fca1891554b5dd38f682fc8406d1))

## [0.7.0](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.6.9...dev.openfeature.contrib.providers.flagd-v0.7.0) (2024-02-22)


### ⚠ BREAKING CHANGES

* use new eval/sync protos (requires flagd v0.7.3+) ([#683](https://github.com/open-feature/java-sdk-contrib/issues/683))

### 🐛 Bug Fixes

* **deps:** update dependency com.networknt:json-schema-validator to v1.2.0 ([#643](https://github.com/open-feature/java-sdk-contrib/issues/643)) ([858d1f6](https://github.com/open-feature/java-sdk-contrib/commit/858d1f660b13b1997ceca54f653923aaa11ea961))
* **deps:** update dependency com.networknt:json-schema-validator to v1.3.0 ([#652](https://github.com/open-feature/java-sdk-contrib/issues/652)) ([4a2cca0](https://github.com/open-feature/java-sdk-contrib/commit/4a2cca07fc64d26c3ad199c96fea2a7806db7069))
* **deps:** update dependency com.networknt:json-schema-validator to v1.3.1 ([#654](https://github.com/open-feature/java-sdk-contrib/issues/654)) ([df469c9](https://github.com/open-feature/java-sdk-contrib/commit/df469c951f8c1d8e8c2d85a9f243cfdca5e0bf76))
* **deps:** update dependency com.networknt:json-schema-validator to v1.3.2 ([#667](https://github.com/open-feature/java-sdk-contrib/issues/667)) ([73d22c6](https://github.com/open-feature/java-sdk-contrib/commit/73d22c6eb7f1f94b6f39a71434045a2dccb5a6f5))
* **deps:** update dependency com.networknt:json-schema-validator to v1.3.3 ([#684](https://github.com/open-feature/java-sdk-contrib/issues/684)) ([f455d8e](https://github.com/open-feature/java-sdk-contrib/commit/f455d8e1d9b030ac6acaeefcb4aa961b43a5ddcf))
* **deps:** update dependency commons-codec:commons-codec to v1.16.1 ([#670](https://github.com/open-feature/java-sdk-contrib/issues/670)) ([6f55ce3](https://github.com/open-feature/java-sdk-contrib/commit/6f55ce383b7542961b5807899813b680d07097f7))
* **deps:** update dependency io.netty:netty-transport-native-epoll to v4.1.105.final ([#634](https://github.com/open-feature/java-sdk-contrib/issues/634)) ([4f6c150](https://github.com/open-feature/java-sdk-contrib/commit/4f6c15025e2b7fffbcf0a63f81d43f20628f0b4d))
* **deps:** update dependency io.netty:netty-transport-native-epoll to v4.1.106.final ([#642](https://github.com/open-feature/java-sdk-contrib/issues/642)) ([2755b68](https://github.com/open-feature/java-sdk-contrib/commit/2755b68d2cfd39110ae32bcc41be1d700dbdb85e))
* **deps:** update dependency io.netty:netty-transport-native-epoll to v4.1.107.final ([#677](https://github.com/open-feature/java-sdk-contrib/issues/677)) ([eb6383d](https://github.com/open-feature/java-sdk-contrib/commit/eb6383d86a5e191605443d6a5e6ee686f5ef0dd1))
* **deps:** update dependency io.opentelemetry:opentelemetry-api to v1.34.1 ([#626](https://github.com/open-feature/java-sdk-contrib/issues/626)) ([220be29](https://github.com/open-feature/java-sdk-contrib/commit/220be295ad32f89a8da241bfca71efcef876350f))
* **deps:** update grpc-java monorepo to v1.61.1 ([#656](https://github.com/open-feature/java-sdk-contrib/issues/656)) ([c4ed3b0](https://github.com/open-feature/java-sdk-contrib/commit/c4ed3b008a927504d9bc3e1613a897b5ba41beab))
* **deps:** update io.grpc.version to v1.61.0 ([#628](https://github.com/open-feature/java-sdk-contrib/issues/628)) ([29c9854](https://github.com/open-feature/java-sdk-contrib/commit/29c9854d5ae58883cd2d169b01fc99f80dd30694))
* **deps:** update opentelemetry-java monorepo to v1.35.0 ([#673](https://github.com/open-feature/java-sdk-contrib/issues/673)) ([4a62744](https://github.com/open-feature/java-sdk-contrib/commit/4a62744767f4924452898030932caac602aa7e02))
* targeting key sometimes missing in rule context ([#676](https://github.com/open-feature/java-sdk-contrib/issues/676)) ([7407b84](https://github.com/open-feature/java-sdk-contrib/commit/7407b84b9e9ac4e129ca372c48891e3c4927894b))


### ✨ New Features

* flagd add scope to in-process evaluations ([#637](https://github.com/open-feature/java-sdk-contrib/issues/637)) ([b3873ae](https://github.com/open-feature/java-sdk-contrib/commit/b3873aea1588be8a54ba94ccd9a1232645cb541d))
* synchronize initialization and shutdown ([#635](https://github.com/open-feature/java-sdk-contrib/issues/635)) ([2d98cb8](https://github.com/open-feature/java-sdk-contrib/commit/2d98cb8367eabde9f4c8fab1cc06db10a2bda903))
* use new eval/sync protos (requires flagd v0.7.3+) ([#683](https://github.com/open-feature/java-sdk-contrib/issues/683)) ([20ca053](https://github.com/open-feature/java-sdk-contrib/commit/20ca05338b9717de5bb12a883096c1f71b0599eb))


### 🧹 Chore

* **deps:** update dependency org.codehaus.mojo:exec-maven-plugin to v3.2.0 ([#690](https://github.com/open-feature/java-sdk-contrib/issues/690)) ([07f7ae9](https://github.com/open-feature/java-sdk-contrib/commit/07f7ae9bd0cdae8b6cf4aa09a0313b28340c658b))
* flagd add offline flag source path support through env variables ([#647](https://github.com/open-feature/java-sdk-contrib/issues/647)) ([cd0e110](https://github.com/open-feature/java-sdk-contrib/commit/cd0e1103114e4cd7c3b79d39a590ec7313f1a566))
* various improvements as suggested by sonar ([#674](https://github.com/open-feature/java-sdk-contrib/issues/674)) ([07eb45a](https://github.com/open-feature/java-sdk-contrib/commit/07eb45a581152d10ebcca9e494cb6e796c6ef0ab))

## [0.6.9](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.6.8...dev.openfeature.contrib.providers.flagd-v0.6.9) (2024-01-09)


### 🐛 Bug Fixes

* **deps:** update dependency com.fasterxml.jackson.core:jackson-databind to v2.16.1 ([#604](https://github.com/open-feature/java-sdk-contrib/issues/604)) ([165b1db](https://github.com/open-feature/java-sdk-contrib/commit/165b1db0a793b628ab6dd05b161350310505e41d))
* **deps:** update dependency com.networknt:json-schema-validator to v1.0.88 ([#579](https://github.com/open-feature/java-sdk-contrib/issues/579)) ([ea9917c](https://github.com/open-feature/java-sdk-contrib/commit/ea9917c86307e4f1f734ffa32343d8ffb16d875f))
* **deps:** update dependency com.networknt:json-schema-validator to v1.1.0 ([#591](https://github.com/open-feature/java-sdk-contrib/issues/591)) ([cb44eab](https://github.com/open-feature/java-sdk-contrib/commit/cb44eab1d4d054c8156fbbb0bb8bc497bcb55604))
* **deps:** update dependency io.grpc:grpc-stub to v1.60.0 ([#573](https://github.com/open-feature/java-sdk-contrib/issues/573)) ([c77bd44](https://github.com/open-feature/java-sdk-contrib/commit/c77bd4428bd2ba7d2df19684678da1e3ebb9812b))
* **deps:** update dependency io.netty:netty-transport-native-epoll to v4.1.103.final ([#586](https://github.com/open-feature/java-sdk-contrib/issues/586)) ([dcd058c](https://github.com/open-feature/java-sdk-contrib/commit/dcd058c7980fb51b3b11f2600ba08b00282270b1))
* **deps:** update dependency io.netty:netty-transport-native-epoll to v4.1.104.final ([#588](https://github.com/open-feature/java-sdk-contrib/issues/588)) ([d2ebcc2](https://github.com/open-feature/java-sdk-contrib/commit/d2ebcc22ca7255890b8cff99e2828b41596af513))
* **deps:** update dependency io.opentelemetry:opentelemetry-api to v1.33.0 ([#582](https://github.com/open-feature/java-sdk-contrib/issues/582)) ([2012a0e](https://github.com/open-feature/java-sdk-contrib/commit/2012a0e119f1abaa7a2b7add052363262d337915))
* **deps:** update dependency io.opentelemetry:opentelemetry-api to v1.34.0 ([#615](https://github.com/open-feature/java-sdk-contrib/issues/615)) ([5822a0a](https://github.com/open-feature/java-sdk-contrib/commit/5822a0a120fad7771895cd5a559e33472b1892a2))
* **deps:** update io.grpc.version to v1.60.1 ([#597](https://github.com/open-feature/java-sdk-contrib/issues/597)) ([b657df1](https://github.com/open-feature/java-sdk-contrib/commit/b657df1d8755d89af38edbf3ee0dd24a9ec6491b))
* edge cases with flagd targeting ([#567](https://github.com/open-feature/java-sdk-contrib/issues/567)) ([7da7d2a](https://github.com/open-feature/java-sdk-contrib/commit/7da7d2a1dfab602b257e801adb689c60ced6aaf3))
* flagd caching ([#581](https://github.com/open-feature/java-sdk-contrib/issues/581)) ([e953fef](https://github.com/open-feature/java-sdk-contrib/commit/e953fef23f98938b90fad33123a9289ca41a3762))


### ✨ New Features

* flagd file polling for offline mode ([#614](https://github.com/open-feature/java-sdk-contrib/issues/614)) ([5e97b12](https://github.com/open-feature/java-sdk-contrib/commit/5e97b129708e864b5ae286535d5c98510ce17d07))


### 🧹 Chore

* add e2e test for reconnect ([#596](https://github.com/open-feature/java-sdk-contrib/issues/596)) ([c22b90e](https://github.com/open-feature/java-sdk-contrib/commit/c22b90ef26c4c58e126eaabff4d3e1770a636347))
* fix types in flagd readme ([e1ac5d5](https://github.com/open-feature/java-sdk-contrib/commit/e1ac5d50c4630b9c757f23989d8597371bbc610c))
* update io.grpc, use shared version ([#580](https://github.com/open-feature/java-sdk-contrib/issues/580)) ([59cdd74](https://github.com/open-feature/java-sdk-contrib/commit/59cdd747b01d68f25c85942cce2bcb8eeae61b92))

## [0.6.8](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.6.7...dev.openfeature.contrib.providers.flagd-v0.6.8) (2023-11-28)


### 🐛 Bug Fixes

* **deps:** update dependency io.grpc:grpc-netty to v1.59.1 ([#559](https://github.com/open-feature/java-sdk-contrib/issues/559)) ([98abe08](https://github.com/open-feature/java-sdk-contrib/commit/98abe08236d61dfaa31265d31813c3a90c0dbc1b))
* **deps:** update dependency io.grpc:grpc-protobuf to v1.59.1 ([#560](https://github.com/open-feature/java-sdk-contrib/issues/560)) ([66d6c47](https://github.com/open-feature/java-sdk-contrib/commit/66d6c47b1b7557e9af5756dddf9e9f5498213b60))
* **deps:** update dependency io.grpc:grpc-stub to v1.59.1 ([#561](https://github.com/open-feature/java-sdk-contrib/issues/561)) ([8c99124](https://github.com/open-feature/java-sdk-contrib/commit/8c99124cd67a02660b4f7e93aff43b9a0da8b1a5))

## [0.6.7](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.6.6...dev.openfeature.contrib.providers.flagd-v0.6.7) (2023-11-21)


### 🐛 Bug Fixes

* **deps:** update dependency com.fasterxml.jackson.core:jackson-databind to v2.15.3 ([#493](https://github.com/open-feature/java-sdk-contrib/issues/493)) ([f6cb68f](https://github.com/open-feature/java-sdk-contrib/commit/f6cb68f3c54e67a10f7e2f5bf9f5a2a840689491))
* **deps:** update dependency com.fasterxml.jackson.core:jackson-databind to v2.16.0 ([#538](https://github.com/open-feature/java-sdk-contrib/issues/538)) ([4857448](https://github.com/open-feature/java-sdk-contrib/commit/485744828ee3c2dc772ada94cb443b516adc9f78))
* **deps:** update dependency io.grpc:grpc-netty to v1.59.0 ([#509](https://github.com/open-feature/java-sdk-contrib/issues/509)) ([cb00b6d](https://github.com/open-feature/java-sdk-contrib/commit/cb00b6df92901701632673d566eecd681b9b21b8))
* **deps:** update dependency io.grpc:grpc-protobuf to v1.59.0 ([#510](https://github.com/open-feature/java-sdk-contrib/issues/510)) ([9df6523](https://github.com/open-feature/java-sdk-contrib/commit/9df65239b591cf527b9b5501eea0fee2aebb5d04))
* **deps:** update dependency io.grpc:grpc-stub to v1.59.0 ([#511](https://github.com/open-feature/java-sdk-contrib/issues/511)) ([9c5df8e](https://github.com/open-feature/java-sdk-contrib/commit/9c5df8e264c3d339684b331f65475f92e809032b))
* **deps:** update dependency io.netty:netty-transport-native-epoll to v4.1.100.final ([#482](https://github.com/open-feature/java-sdk-contrib/issues/482)) ([9c08799](https://github.com/open-feature/java-sdk-contrib/commit/9c087997281cdc83de84a01743c2fcef8e1fb0d1))
* **deps:** update dependency io.netty:netty-transport-native-epoll to v4.1.101.final ([#532](https://github.com/open-feature/java-sdk-contrib/issues/532)) ([4e293bf](https://github.com/open-feature/java-sdk-contrib/commit/4e293bfa3f761aab82986c76efef375112a42154))
* **deps:** update dependency io.opentelemetry:opentelemetry-api to v1.32.0 ([#534](https://github.com/open-feature/java-sdk-contrib/issues/534)) ([9d66306](https://github.com/open-feature/java-sdk-contrib/commit/9d66306094146f1983852d06506609e51b5983d9))
* set flag key on `$flagd.flagKey` ([#492](https://github.com/open-feature/java-sdk-contrib/issues/492)) ([934f934](https://github.com/open-feature/java-sdk-contrib/commit/934f9340b613cde127377d80b576a685843070f7))


### ✨ New Features

* `$flagd.timestamp` added to in-process evaluator  ([#512](https://github.com/open-feature/java-sdk-contrib/issues/512)) ([3a074b2](https://github.com/open-feature/java-sdk-contrib/commit/3a074b29ebbfb0300591d36ded3d08c2f1ae84a9))
* Allow global otel configuration extraction ([#505](https://github.com/open-feature/java-sdk-contrib/issues/505)) ([addbc31](https://github.com/open-feature/java-sdk-contrib/commit/addbc310249f0e6c6c753827ea4b48bdbfe02a43))
* utilize initialization context for flag evaluation ([#550](https://github.com/open-feature/java-sdk-contrib/issues/550)) ([2f3c069](https://github.com/open-feature/java-sdk-contrib/commit/2f3c0694987b7a90c3f59abe11d5511047a0d4c3))


### 🧹 Chore

* bundle flagd new proto schems ([#551](https://github.com/open-feature/java-sdk-contrib/issues/551)) ([478d593](https://github.com/open-feature/java-sdk-contrib/commit/478d593eba55fd64fb719d6ad648ca768d11916b))
* **deps:** update dependency org.codehaus.mojo:exec-maven-plugin to v3.1.1 ([#543](https://github.com/open-feature/java-sdk-contrib/issues/543)) ([56ebf39](https://github.com/open-feature/java-sdk-contrib/commit/56ebf39a26d3ef09c4fdec70b48e0911335dbc85))
* remove experimental badge ([#522](https://github.com/open-feature/java-sdk-contrib/issues/522)) ([ffc1c3b](https://github.com/open-feature/java-sdk-contrib/commit/ffc1c3be1bfd870f1c265690a068ee5f143ba3bb))

## [0.6.6](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.6.5...dev.openfeature.contrib.providers.flagd-v0.6.6) (2023-10-10)


### 🐛 Bug Fixes

* **deps:** update dependency org.semver4j:semver4j to v5.2.2 ([#480](https://github.com/open-feature/java-sdk-contrib/issues/480)) ([05a2535](https://github.com/open-feature/java-sdk-contrib/commit/05a2535f6223d923ce878e824dbac0ba1c06415e))


### ✨ New Features

* flagd in process offline mode ([#473](https://github.com/open-feature/java-sdk-contrib/issues/473)) ([6920557](https://github.com/open-feature/java-sdk-contrib/commit/6920557708528fa858b3febd7f0700255e598576))

## [0.6.5](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.6.4...dev.openfeature.contrib.providers.flagd-v0.6.5) (2023-10-09)


### 🐛 Bug Fixes

* **deps:** update dependency io.opentelemetry:opentelemetry-api to v1.31.0 ([#474](https://github.com/open-feature/java-sdk-contrib/issues/474)) ([f4f28d8](https://github.com/open-feature/java-sdk-contrib/commit/f4f28d8413f56d25355ce0331b6cf1f8b8de7229))
* int/float auto-conversion ([#472](https://github.com/open-feature/java-sdk-contrib/issues/472)) ([63b541c](https://github.com/open-feature/java-sdk-contrib/commit/63b541c166188552764da9df3326754ea6d65b77))


### 🧹 Chore

* fix dependencies for flagd and OTel hook ([#471](https://github.com/open-feature/java-sdk-contrib/issues/471)) ([8a0c8cf](https://github.com/open-feature/java-sdk-contrib/commit/8a0c8cfa675363ecbef5467fea7ffc28d4b69e97))
* flagd change log level from error to warn ([#465](https://github.com/open-feature/java-sdk-contrib/issues/465)) ([d03be0c](https://github.com/open-feature/java-sdk-contrib/commit/d03be0c366876a84bcf169d5404ea2ea144e7fab))

## [0.6.4](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.6.3...dev.openfeature.contrib.providers.flagd-v0.6.4) (2023-09-28)


### Features

* force update gson depdency ([#460](https://github.com/open-feature/java-sdk-contrib/issues/460)) ([269d284](https://github.com/open-feature/java-sdk-contrib/commit/269d28448834c2ea06e8daa9fef3bd48d501c702))
* jul to slf4j ([#458](https://github.com/open-feature/java-sdk-contrib/issues/458)) ([a90a864](https://github.com/open-feature/java-sdk-contrib/commit/a90a864a668453e8a0f47af4813cdb669696f678))


### Bug Fixes

* **deps:** update dependency io.netty:netty-transport-native-epoll to v4.1.99.final ([#464](https://github.com/open-feature/java-sdk-contrib/issues/464)) ([9456f63](https://github.com/open-feature/java-sdk-contrib/commit/9456f63f736d596ba31d550db7a8e7cb2b89f256))
* **deps:** update dependency org.semver4j:semver4j to v5.2.1 ([#456](https://github.com/open-feature/java-sdk-contrib/issues/456)) ([7aee884](https://github.com/open-feature/java-sdk-contrib/commit/7aee884e25322b6868d649835439da441c57802c))

## [0.6.3](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.6.2...dev.openfeature.contrib.providers.flagd-v0.6.3) (2023-09-22)


### Features

* flagd in-process evalator improvements ([#451](https://github.com/open-feature/java-sdk-contrib/issues/451)) ([a96c5d8](https://github.com/open-feature/java-sdk-contrib/commit/a96c5d8f72cbcc528e4db9a1981e911658d1caae))


### Bug Fixes

* await shutdown in in-process mode ([#445](https://github.com/open-feature/java-sdk-contrib/issues/445)) ([49340ef](https://github.com/open-feature/java-sdk-contrib/commit/49340ef1dd4539eb313b6da52bd5e3e2b66784cc))
* **deps:** update dependency io.netty:netty-transport-native-epoll to v4.1.98.final ([#444](https://github.com/open-feature/java-sdk-contrib/issues/444)) ([8ceff09](https://github.com/open-feature/java-sdk-contrib/commit/8ceff09244e13136b740be711883fe89deb1fa7f))
* **deps:** update dependency org.semver4j:semver4j to v5.2.0 ([#450](https://github.com/open-feature/java-sdk-contrib/issues/450)) ([2b8f978](https://github.com/open-feature/java-sdk-contrib/commit/2b8f978e9f3d513242bc1dc81fd6114e07ea8648))

## [0.6.2](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.6.1...dev.openfeature.contrib.providers.flagd-v0.6.2) (2023-09-19)


### Features

* flagd in-process provider ([#412](https://github.com/open-feature/java-sdk-contrib/issues/412)) ([7accd1e](https://github.com/open-feature/java-sdk-contrib/commit/7accd1e8b68a06714d7e2accb8d226eb364db49a))
* json logic operators for flagd in-process provider ([#434](https://github.com/open-feature/java-sdk-contrib/issues/434)) ([485c8a3](https://github.com/open-feature/java-sdk-contrib/commit/485c8a38e9055bcc1ebadad77a09b514d002525a))


### Bug Fixes

* blocking in-process init, e2e tests ([#436](https://github.com/open-feature/java-sdk-contrib/issues/436)) ([0326095](https://github.com/open-feature/java-sdk-contrib/commit/032609572da04b347d5d0b1e5ae1542e43ada1c1))
* **deps:** update dependency com.networknt:json-schema-validator to v1.0.87 ([#426](https://github.com/open-feature/java-sdk-contrib/issues/426)) ([77ec448](https://github.com/open-feature/java-sdk-contrib/commit/77ec4484763eb8883d9dc4ecdbfb8d7162f03c70))
* **deps:** update dependency io.grpc:grpc-netty to v1.58.0 ([#421](https://github.com/open-feature/java-sdk-contrib/issues/421)) ([496bdec](https://github.com/open-feature/java-sdk-contrib/commit/496bdec854bf300c2db4c30800dd5518b836436f))
* **deps:** update dependency io.grpc:grpc-protobuf to v1.58.0 ([#422](https://github.com/open-feature/java-sdk-contrib/issues/422)) ([54f24dd](https://github.com/open-feature/java-sdk-contrib/commit/54f24ddf68c6abdb037432427f7028ecfe182072))
* **deps:** update dependency io.grpc:grpc-stub to v1.58.0 ([#423](https://github.com/open-feature/java-sdk-contrib/issues/423)) ([ea4268d](https://github.com/open-feature/java-sdk-contrib/commit/ea4268d48420a76c5b0414c3b05b8e73dfe990a2))
* **deps:** update dependency io.netty:netty-transport-native-epoll to v4.1.97.final ([#406](https://github.com/open-feature/java-sdk-contrib/issues/406)) ([ed25450](https://github.com/open-feature/java-sdk-contrib/commit/ed254508929a2b84b04735dad1dccbc1fdc7f173))
* **deps:** update dependency io.opentelemetry:opentelemetry-api to v1.30.0 ([#427](https://github.com/open-feature/java-sdk-contrib/issues/427)) ([3667e45](https://github.com/open-feature/java-sdk-contrib/commit/3667e45ba840f2e086dc530917b1672247376cec))
* **deps:** update dependency io.opentelemetry:opentelemetry-api to v1.30.1 ([#432](https://github.com/open-feature/java-sdk-contrib/issues/432)) ([3954230](https://github.com/open-feature/java-sdk-contrib/commit/395423076a92ff3408189742ae9d9e9e87ae8f39))

## [0.6.1](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.6.0...dev.openfeature.contrib.providers.flagd-v0.6.1) (2023-08-21)


### Features

* flagd flag evaluation metadata ([#389](https://github.com/open-feature/java-sdk-contrib/issues/389)) ([b571cc5](https://github.com/open-feature/java-sdk-contrib/commit/b571cc5eb8424563dfed46e686b771da253894ee))


### Bug Fixes

* **deps:** update dependency io.grpc:grpc-netty to v1.57.0 ([#373](https://github.com/open-feature/java-sdk-contrib/issues/373)) ([3b00f7d](https://github.com/open-feature/java-sdk-contrib/commit/3b00f7d5855a2f37e4bdaa5604a89078652210a1))
* **deps:** update dependency io.grpc:grpc-netty to v1.57.1 ([#382](https://github.com/open-feature/java-sdk-contrib/issues/382)) ([72ca252](https://github.com/open-feature/java-sdk-contrib/commit/72ca252a5a09dd3ca4fb7ebd1dd407b0b7cdca07))
* **deps:** update dependency io.grpc:grpc-netty to v1.57.2 ([#399](https://github.com/open-feature/java-sdk-contrib/issues/399)) ([a461bf0](https://github.com/open-feature/java-sdk-contrib/commit/a461bf01a874b5f6af04cb98231ad9bbfc5a3fde))
* **deps:** update dependency io.grpc:grpc-protobuf to v1.57.0 ([#374](https://github.com/open-feature/java-sdk-contrib/issues/374)) ([f8d11d6](https://github.com/open-feature/java-sdk-contrib/commit/f8d11d6966208909eedcdb64cce1613aed2ba3b9))
* **deps:** update dependency io.grpc:grpc-protobuf to v1.57.1 ([#383](https://github.com/open-feature/java-sdk-contrib/issues/383)) ([492b437](https://github.com/open-feature/java-sdk-contrib/commit/492b437b48bb225d74ae9b0154003bb1e6202a97))
* **deps:** update dependency io.grpc:grpc-protobuf to v1.57.2 ([#400](https://github.com/open-feature/java-sdk-contrib/issues/400)) ([9750f99](https://github.com/open-feature/java-sdk-contrib/commit/9750f99bc8d342b698ea0fc932fb481f962e2b9d))
* **deps:** update dependency io.grpc:grpc-stub to v1.57.0 ([#375](https://github.com/open-feature/java-sdk-contrib/issues/375)) ([6b53cf4](https://github.com/open-feature/java-sdk-contrib/commit/6b53cf42ca7fc74fbd069a90c0779f0088eb081e))
* **deps:** update dependency io.grpc:grpc-stub to v1.57.1 ([#384](https://github.com/open-feature/java-sdk-contrib/issues/384)) ([6cf9900](https://github.com/open-feature/java-sdk-contrib/commit/6cf9900c8cab3e958dbf8ebb53553c51a94f872e))
* **deps:** update dependency io.grpc:grpc-stub to v1.57.2 ([#401](https://github.com/open-feature/java-sdk-contrib/issues/401)) ([1d612a9](https://github.com/open-feature/java-sdk-contrib/commit/1d612a91ab3884bd1368d99d1f1e183d9b14e374))
* **deps:** update dependency io.netty:netty-transport-native-epoll to v4.1.96.final ([#372](https://github.com/open-feature/java-sdk-contrib/issues/372)) ([f2eaca2](https://github.com/open-feature/java-sdk-contrib/commit/f2eaca2f7c18f44f131cf259d077bf39e349c00f))
* **deps:** update dependency io.opentelemetry:opentelemetry-api to v1.29.0 ([#396](https://github.com/open-feature/java-sdk-contrib/issues/396)) ([21ff548](https://github.com/open-feature/java-sdk-contrib/commit/21ff5489fc641d03c16fd9a71a610488cfa7db5c))

## [0.6.0](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.5.10...dev.openfeature.contrib.providers.flagd-v0.6.0) (2023-07-27)


### ⚠ BREAKING CHANGES

* add events support ([#361](https://github.com/open-feature/java-sdk-contrib/issues/361))
  * require newer SDK version 

### Features

* add events support ([#361](https://github.com/open-feature/java-sdk-contrib/issues/361)) ([258ca10](https://github.com/open-feature/java-sdk-contrib/commit/258ca10c815e61f782442fb3b8c87cd1dd79fe75))


### Bug Fixes

* **deps:** update dependency io.grpc:grpc-netty to v1.56.0 ([#340](https://github.com/open-feature/java-sdk-contrib/issues/340)) ([641f26b](https://github.com/open-feature/java-sdk-contrib/commit/641f26bb31d84724f9cdd50b92757fc4c0c9adcc))
* **deps:** update dependency io.grpc:grpc-netty to v1.56.1 ([#349](https://github.com/open-feature/java-sdk-contrib/issues/349)) ([716cdfa](https://github.com/open-feature/java-sdk-contrib/commit/716cdfaff3ef88302300d2089103d167d9a38033))
* **deps:** update dependency io.grpc:grpc-protobuf to v1.56.0 ([#341](https://github.com/open-feature/java-sdk-contrib/issues/341)) ([d57285d](https://github.com/open-feature/java-sdk-contrib/commit/d57285db67b795b185940d12e883a8c2fa8bd6f5))
* **deps:** update dependency io.grpc:grpc-protobuf to v1.56.1 ([#350](https://github.com/open-feature/java-sdk-contrib/issues/350)) ([55e9f06](https://github.com/open-feature/java-sdk-contrib/commit/55e9f061a9ac5fe847029fd56461219572b77643))
* **deps:** update dependency io.grpc:grpc-stub to v1.56.0 ([#342](https://github.com/open-feature/java-sdk-contrib/issues/342)) ([141baad](https://github.com/open-feature/java-sdk-contrib/commit/141baad57f966d3a3e90084613efbf6acbadd7ed))
* **deps:** update dependency io.grpc:grpc-stub to v1.56.1 ([#351](https://github.com/open-feature/java-sdk-contrib/issues/351)) ([dbead5b](https://github.com/open-feature/java-sdk-contrib/commit/dbead5bd31e6368dcb4681476abec3deab28966a))
* **deps:** update dependency io.netty:netty-transport-native-epoll to v4.1.93.final ([#326](https://github.com/open-feature/java-sdk-contrib/issues/326)) ([142d516](https://github.com/open-feature/java-sdk-contrib/commit/142d5164c17d9cfdc63c1853d181fd5f2e1dbcbe))
* **deps:** update dependency io.netty:netty-transport-native-epoll to v4.1.94.final ([#346](https://github.com/open-feature/java-sdk-contrib/issues/346)) ([8e8cc05](https://github.com/open-feature/java-sdk-contrib/commit/8e8cc051df3fe7795162db3971c39d5152a3efc7))
* **deps:** update dependency io.netty:netty-transport-native-epoll to v4.1.95.final ([#362](https://github.com/open-feature/java-sdk-contrib/issues/362)) ([147453e](https://github.com/open-feature/java-sdk-contrib/commit/147453ed4e0a8c2591686fe623de69fb5def3fa2))
* **deps:** update dependency io.opentelemetry:opentelemetry-api to v1.27.0 ([#338](https://github.com/open-feature/java-sdk-contrib/issues/338)) ([a0d2753](https://github.com/open-feature/java-sdk-contrib/commit/a0d2753c1b80c5ce67025a4e31698bb67cc8dfc0))
* **deps:** update dependency io.opentelemetry:opentelemetry-api to v1.28.0 ([#354](https://github.com/open-feature/java-sdk-contrib/issues/354)) ([220b01a](https://github.com/open-feature/java-sdk-contrib/commit/220b01a173f2c1067fce92e0ff39e2a17e5000ec))

## [0.5.10](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.5.9...dev.openfeature.contrib.providers.flagd-v0.5.10) (2023-05-09)


### Bug Fixes

* **deps:** update dependency io.grpc:grpc-netty to v1.55.1 ([#308](https://github.com/open-feature/java-sdk-contrib/issues/308)) ([5c64de5](https://github.com/open-feature/java-sdk-contrib/commit/5c64de5959ff872a3958da8896b7881b60788d34))
* **deps:** update dependency io.grpc:grpc-protobuf to v1.55.1 ([#309](https://github.com/open-feature/java-sdk-contrib/issues/309)) ([537307e](https://github.com/open-feature/java-sdk-contrib/commit/537307e65666c6dfb91a715722ba913268f847cd))
* **deps:** update dependency io.grpc:grpc-stub to v1.55.1 ([#310](https://github.com/open-feature/java-sdk-contrib/issues/310)) ([b16dcd5](https://github.com/open-feature/java-sdk-contrib/commit/b16dcd55077961f53ee59c28f1c6d430f15bdf43))
* **deps:** update dependency io.opentelemetry:opentelemetry-api to v1.26.0 ([#302](https://github.com/open-feature/java-sdk-contrib/issues/302)) ([021f048](https://github.com/open-feature/java-sdk-contrib/commit/021f0483ea0606b5298e8822ac5b8ad656338fcf))

## [0.5.9](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.5.8...dev.openfeature.contrib.providers.flagd-v0.5.9) (2023-05-01)

### Deprecated :warning: 

* deprecated existing constructors in favor of builder ([#294](https://github.com/open-feature/java-sdk-contrib/pull/294)) ([428b7d5](https://github.com/open-feature/java-sdk-contrib/commit/428b7d507b39e9e5a5c433160690fe52b2484aa0))

### Features

* flagd manual otel interceptor for grpc ([#286](https://github.com/open-feature/java-sdk-contrib/issues/286)) ([9168797](https://github.com/open-feature/java-sdk-contrib/commit/9168797370e565b7ceb2b25ae4cb940ad8e0dcf3))


### Bug Fixes

* **deps:** update dependency io.grpc:grpc-netty to v1.54.1 ([#279](https://github.com/open-feature/java-sdk-contrib/issues/279)) ([94fbc0b](https://github.com/open-feature/java-sdk-contrib/commit/94fbc0b2bb4901b675f705be593815e4f704090f))
* **deps:** update dependency io.grpc:grpc-protobuf to v1.54.1 ([#280](https://github.com/open-feature/java-sdk-contrib/issues/280)) ([d0cef25](https://github.com/open-feature/java-sdk-contrib/commit/d0cef251eda249c8398d50fef4d8819bbb8b8300))
* **deps:** update dependency io.grpc:grpc-stub to v1.54.1 ([#281](https://github.com/open-feature/java-sdk-contrib/issues/281)) ([614c3df](https://github.com/open-feature/java-sdk-contrib/commit/614c3dfa0952df4762d64c56867306ecc446c9ed))
* **deps:** update dependency io.netty:netty-transport-native-epoll to v4.1.91.final ([#266](https://github.com/open-feature/java-sdk-contrib/issues/266)) ([94c0146](https://github.com/open-feature/java-sdk-contrib/commit/94c014620919c3311452d2e85b7d53a84726fa4d))
* **deps:** update dependency io.netty:netty-transport-native-epoll to v4.1.92.final ([#296](https://github.com/open-feature/java-sdk-contrib/issues/296)) ([d6e9131](https://github.com/open-feature/java-sdk-contrib/commit/d6e9131129977a3ce750ddc42ec956a07eb61b8c))

## [0.5.8](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.5.7...dev.openfeature.contrib.providers.flagd-v0.5.8) (2023-03-27)


### Bug Fixes

* [flagd]NPE of flagd due to null context value ([#259](https://github.com/open-feature/java-sdk-contrib/issues/259)) ([3ed5166](https://github.com/open-feature/java-sdk-contrib/commit/3ed5166144a9ea55ef6dd4ed21663b6fb663076f))
* **deps:** update dependency io.grpc:grpc-netty to v1.54.0 ([#251](https://github.com/open-feature/java-sdk-contrib/issues/251)) ([a7450a8](https://github.com/open-feature/java-sdk-contrib/commit/a7450a88e2f485243197d8a4952959b6977b28cd))
* **deps:** update dependency io.grpc:grpc-protobuf to v1.54.0 ([#252](https://github.com/open-feature/java-sdk-contrib/issues/252)) ([559ddae](https://github.com/open-feature/java-sdk-contrib/commit/559ddaefc809fdaa13ebd77173d477d1aa35b86b))
* **deps:** update dependency io.grpc:grpc-stub to v1.54.0 ([#253](https://github.com/open-feature/java-sdk-contrib/issues/253)) ([984776a](https://github.com/open-feature/java-sdk-contrib/commit/984776a1bb735639112d905411bf5bccd21395ec))

## [0.5.7](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.5.6...dev.openfeature.contrib.providers.flagd-v0.5.7) (2023-03-21)


### Bug Fixes

* **deps:** update dependency io.grpc:grpc-netty to v1.53.0 ([#216](https://github.com/open-feature/java-sdk-contrib/issues/216)) ([ca8f68f](https://github.com/open-feature/java-sdk-contrib/commit/ca8f68f123d0468978f9991e3eb302b92fab1ab8))
* **deps:** update dependency io.grpc:grpc-protobuf to v1.53.0 ([#217](https://github.com/open-feature/java-sdk-contrib/issues/217)) ([5323e1d](https://github.com/open-feature/java-sdk-contrib/commit/5323e1d90aab62a7721bd524dd4906f6f729b2f4))
* **deps:** update dependency io.grpc:grpc-stub to v1.53.0 ([#218](https://github.com/open-feature/java-sdk-contrib/issues/218)) ([e711e8c](https://github.com/open-feature/java-sdk-contrib/commit/e711e8c33f6ecfb5b2507dbd66f5f6e0a70789c0))
* **deps:** update dependency io.netty:netty-transport-native-epoll to v4.1.88.final ([#222](https://github.com/open-feature/java-sdk-contrib/issues/222)) ([51dc9ee](https://github.com/open-feature/java-sdk-contrib/commit/51dc9ee092ecf3903853c9324595d294d1b540ee))
* **deps:** update dependency io.netty:netty-transport-native-epoll to v4.1.89.final ([#224](https://github.com/open-feature/java-sdk-contrib/issues/224)) ([4406441](https://github.com/open-feature/java-sdk-contrib/commit/440644114a9c1d66214f0d9590892c57fef54f76))
* **deps:** update dependency io.netty:netty-transport-native-epoll to v4.1.90.final ([#240](https://github.com/open-feature/java-sdk-contrib/issues/240)) ([20635f6](https://github.com/open-feature/java-sdk-contrib/commit/20635f64ee7528aa45cb6c692d48f00e857a655f))

## [0.5.6](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.5.5...dev.openfeature.contrib.providers.flagd-v0.5.6) (2023-01-19)


### Features

* exposed eventStreamAliveSync to allow application authors to block until event stream is alive ([6ab8521](https://github.com/open-feature/java-sdk-contrib/commit/6ab8521f4d88160c32ef528397d53678005c39f7))
* exposed eventStreamAliveSync to allow application authors to block until event stream is alive ([#204](https://github.com/open-feature/java-sdk-contrib/issues/204)) ([6ab8521](https://github.com/open-feature/java-sdk-contrib/commit/6ab8521f4d88160c32ef528397d53678005c39f7))


### Bug Fixes

* **deps:** update dependency io.grpc:grpc-netty to v1.52.1 ([#196](https://github.com/open-feature/java-sdk-contrib/issues/196)) ([8eebdb1](https://github.com/open-feature/java-sdk-contrib/commit/8eebdb1a539b98b09b54a763821df869b64d24d7))
* **deps:** update dependency io.grpc:grpc-protobuf to v1.52.1 ([#197](https://github.com/open-feature/java-sdk-contrib/issues/197)) ([9821865](https://github.com/open-feature/java-sdk-contrib/commit/98218659811372faf3e3bcc753c494db3a546a1a))
* **deps:** update dependency io.grpc:grpc-stub to v1.52.1 ([#198](https://github.com/open-feature/java-sdk-contrib/issues/198)) ([6fb0f45](https://github.com/open-feature/java-sdk-contrib/commit/6fb0f4552f9a2d5a35205c661019d95131fe91f2))

## [0.5.5](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.5.4...dev.openfeature.contrib.providers.flagd-v0.5.5) (2023-01-17)


### Features

* flagd caching ([#168](https://github.com/open-feature/java-sdk-contrib/issues/168)) ([528504f](https://github.com/open-feature/java-sdk-contrib/commit/528504fa0562ffa5f3d12dcd5d69e355ea542f11))


### Bug Fixes

* allow flagd-provider cache to be disabled ([#201](https://github.com/open-feature/java-sdk-contrib/issues/201)) ([f505b83](https://github.com/open-feature/java-sdk-contrib/commit/f505b83d0f29f7d59eb7bea999d5a8bc441706ef))
* **deps:** update dependency io.grpc:grpc-netty to v1.52.0 ([#189](https://github.com/open-feature/java-sdk-contrib/issues/189)) ([246e837](https://github.com/open-feature/java-sdk-contrib/commit/246e8376246776518dc0995bfa2999ec29537f92))
* **deps:** update dependency io.grpc:grpc-protobuf to v1.52.0 ([#190](https://github.com/open-feature/java-sdk-contrib/issues/190)) ([283744b](https://github.com/open-feature/java-sdk-contrib/commit/283744b007e28b570587d7658a9cc9a12335dc69))
* **deps:** update dependency io.grpc:grpc-stub to v1.52.0 ([#191](https://github.com/open-feature/java-sdk-contrib/issues/191)) ([7245a91](https://github.com/open-feature/java-sdk-contrib/commit/7245a91a8f137bc2fc146449025fa6c5b4422c2f))
* **deps:** update dependency io.netty:netty-transport-native-epoll to v4.1.87.final ([#192](https://github.com/open-feature/java-sdk-contrib/issues/192)) ([bb6775f](https://github.com/open-feature/java-sdk-contrib/commit/bb6775f69c6c95fde27ee5abddf5f851c01cdc52))

## [0.5.4](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.5.3...dev.openfeature.contrib.providers.flagd-v0.5.4) (2022-12-22)


### Bug Fixes

* **deps:** update dependency io.grpc:grpc-netty to v1.51.1 ([#163](https://github.com/open-feature/java-sdk-contrib/issues/163)) ([49a0249](https://github.com/open-feature/java-sdk-contrib/commit/49a024931058b47f93f1159e7f1938c389ed83e9))
* **deps:** update dependency io.grpc:grpc-protobuf to v1.51.0 ([#156](https://github.com/open-feature/java-sdk-contrib/issues/156)) ([c76f6fd](https://github.com/open-feature/java-sdk-contrib/commit/c76f6fdc71801dffe872019ebff95b37768b0cd2))
* **deps:** update dependency io.grpc:grpc-protobuf to v1.51.1 ([#164](https://github.com/open-feature/java-sdk-contrib/issues/164)) ([1cc26ae](https://github.com/open-feature/java-sdk-contrib/commit/1cc26ae5e4afca0972a55b150efca9928b22d510))
* **deps:** update dependency io.grpc:grpc-stub to v1.51.1 ([#158](https://github.com/open-feature/java-sdk-contrib/issues/158)) ([3960500](https://github.com/open-feature/java-sdk-contrib/commit/39605008f245c38a36b999d7b005ec010eb23059))
* **deps:** update dependency io.netty:netty-transport-native-epoll to v4.1.86.final ([#165](https://github.com/open-feature/java-sdk-contrib/issues/165)) ([c4946af](https://github.com/open-feature/java-sdk-contrib/commit/c4946af8fb5d93db5d7927f0a3eb5878b93fe754))

## [0.5.3](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.5.2...dev.openfeature.contrib.providers.flagd-v0.5.3) (2022-11-28)


### Bug Fixes

* make deadline setter public ([#137](https://github.com/open-feature/java-sdk-contrib/issues/137)) ([4a787ed](https://github.com/open-feature/java-sdk-contrib/commit/4a787edd361ba61e95313c8708fdee9fc63fddcc))

## [0.5.2](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.5.1...dev.openfeature.contrib.providers.flagd-v0.5.2) (2022-11-23)


### Features

* flagd-provider ssl, socket, deadline, env support ([#134](https://github.com/open-feature/java-sdk-contrib/issues/134)) ([34ac374](https://github.com/open-feature/java-sdk-contrib/commit/34ac3747f86987cca23d6f22bf32c7c5fffa91c3))

## [0.5.1](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.5.0...dev.openfeature.contrib.providers.flagd-v0.5.1) (2022-11-16)


### Bug Fixes

* public flagd protocol ([#130](https://github.com/open-feature/java-sdk-contrib/issues/130)) ([30f4e7d](https://github.com/open-feature/java-sdk-contrib/commit/30f4e7d728c969cea1cd3d87724dc1c457ec1237))

## [0.5.0](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.4.1...dev.openfeature.contrib.providers.flagd-v0.5.0) (2022-10-27)


### ⚠ BREAKING CHANGES

* use 1.0 sdk (#123)

### Miscellaneous Chores

* use 1.0 sdk ([#123](https://github.com/open-feature/java-sdk-contrib/issues/123)) ([ee78f61](https://github.com/open-feature/java-sdk-contrib/commit/ee78f610f669eff6f90ffc958e1be88ed203350f))

## [0.4.1](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.4.0...dev.openfeature.contrib.providers.flagd-v0.4.1) (2022-10-13)


### Bug Fixes

* update-parent-pom ([#120](https://github.com/open-feature/java-sdk-contrib/issues/120)) ([d8ac4bd](https://github.com/open-feature/java-sdk-contrib/commit/d8ac4bdba6b5d9efb98ea641d50337f0e3ba3139))

## [0.4.0](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.3.2...dev.openfeature.contrib.providers.flagd-v0.4.0) (2022-10-13)


### ⚠ BREAKING CHANGES

* udpate to sdk 0.3.0
* update to sdk 0.3.0 (#116)

### Features

* udpate to sdk 0.3.0 ([8933bb9](https://github.com/open-feature/java-sdk-contrib/commit/8933bb9b4521e44572b67e6784fd7ce6c541d7b8))
* update to sdk 0.3.0 ([#116](https://github.com/open-feature/java-sdk-contrib/issues/116)) ([8933bb9](https://github.com/open-feature/java-sdk-contrib/commit/8933bb9b4521e44572b67e6784fd7ce6c541d7b8))

## [0.3.2](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.3.1...dev.openfeature.contrib.providers.flagd-v0.3.2) (2022-09-16)


### Bug Fixes

* update parent pom ref ([#104](https://github.com/open-feature/java-sdk-contrib/issues/104)) ([1882854](https://github.com/open-feature/java-sdk-contrib/commit/1882854775a881314ae75a178b2c354669b2619a))

## [0.3.1](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.3.0...dev.openfeature.contrib.providers.flagd-v0.3.1) (2022-09-16)


### Bug Fixes

* publish parent pom ([#101](https://github.com/open-feature/java-sdk-contrib/issues/101)) ([b05f604](https://github.com/open-feature/java-sdk-contrib/commit/b05f604e393126e14cc6849760d5a29a3a3c7484))

## [0.3.0](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.2.4...dev.openfeature.contrib.providers.flagd-v0.3.0) (2022-09-16)


### ⚠ BREAKING CHANGES

* update sdk, absorb changes (#97)

### Features

* update sdk, absorb changes ([#97](https://github.com/open-feature/java-sdk-contrib/issues/97)) ([ef1d4fe](https://github.com/open-feature/java-sdk-contrib/commit/ef1d4fe2692d74a973a13b33a0617b2a8b295559))

## [0.2.4](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.2.3...dev.openfeature.contrib.providers.flagd-v0.2.4) (2022-08-31)


### Features

* add flagd provider implementation ([#66](https://github.com/open-feature/java-sdk-contrib/issues/66)) ([8d299c4](https://github.com/open-feature/java-sdk-contrib/commit/8d299c41ad0bef8c3e81cc3c50932f1ee254c644))

## [0.2.3](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.2.2...dev.openfeature.contrib.providers.flagd-v0.2.3) (2022-08-30)


### Bug Fixes

* build issue with single publish ([#81](https://github.com/open-feature/java-sdk-contrib/issues/81)) ([de03321](https://github.com/open-feature/java-sdk-contrib/commit/de0332125253ff61df388caa502dbfecc244531a))
* update javadoc ([#57](https://github.com/open-feature/java-sdk-contrib/issues/57)) ([7a64cfa](https://github.com/open-feature/java-sdk-contrib/commit/7a64cfa0ab835139603e4a582f3a2b91f24207bb))
* update javadoc ([#63](https://github.com/open-feature/java-sdk-contrib/issues/63)) ([3a4b7d8](https://github.com/open-feature/java-sdk-contrib/commit/3a4b7d83e2272d43e252f6a1201c4e3a7aee4330))

## [0.2.2](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.2.1...dev.openfeature.contrib.providers.flagd-v0.2.2) (2022-08-30)


### Bug Fixes

* build issue with single publish ([#81](https://github.com/open-feature/java-sdk-contrib/issues/81)) ([de03321](https://github.com/open-feature/java-sdk-contrib/commit/de0332125253ff61df388caa502dbfecc244531a))

## [0.2.1](https://github.com/open-feature/java-sdk-contrib/compare/dev.openfeature.contrib.providers.flagd-v0.2.0...dev.openfeature.contrib.providers.flagd-v0.2.1) (2022-08-16)


### Bug Fixes

* update javadoc ([#63](https://github.com/open-feature/java-sdk-contrib/issues/63)) ([3a4b7d8](https://github.com/open-feature/java-sdk-contrib/commit/3a4b7d83e2272d43e252f6a1201c4e3a7aee4330))

## 0.2.0 (2022-08-16)


### Bug Fixes

* update javadoc ([#57](https://github.com/open-feature/java-sdk-contrib/issues/57)) ([7a64cfa](https://github.com/open-feature/java-sdk-contrib/commit/7a64cfa0ab835139603e4a582f3a2b91f24207bb))

## 0.2.0 (2022-08-16)


### Bug Fixes

* update javadoc ([#51](https://github.com/open-feature/java-sdk-contrib/issues/51)) ([86357c1](https://github.com/open-feature/java-sdk-contrib/commit/86357c1aec5fec443dc96661bf9e5c3edb100808))

## 0.2.0 (2022-08-16)


### Bug Fixes

* update javadoc ([#49](https://github.com/open-feature/java-sdk-contrib/issues/49)) ([32db62b](https://github.com/open-feature/java-sdk-contrib/commit/32db62b599d5e446f33e314bebcbed951efa5039))

## 0.2.0 (2022-08-16)


### Bug Fixes

* update javadoc ([#46](https://github.com/open-feature/java-sdk-contrib/issues/46)) ([8fb6042](https://github.com/open-feature/java-sdk-contrib/commit/8fb6042370bdbe303b0cbdba8993f97414fd24cc))

## 0.2.0 (2022-08-16)


### Bug Fixes

* update javadoc ([#37](https://github.com/open-feature/java-sdk-contrib/issues/37)) ([3735206](https://github.com/open-feature/java-sdk-contrib/commit/373520679c4d7ce6120b30d0a354ad7040d6f030))

## 0.2.0 (2022-08-16)


### Bug Fixes

* update javadoc ([#23](https://github.com/open-feature/java-sdk-contrib/issues/23)) ([194eb5a](https://github.com/open-feature/java-sdk-contrib/commit/194eb5aa3384b1eab70b4f09084a227219a08eaf))

## 0.1.0 (2022-08-15)


### Bug Fixes

* remove snapshots ([#5](https://github.com/open-feature/java-sdk-contrib/issues/5)) ([c9d937b](https://github.com/open-feature/java-sdk-contrib/commit/c9d937b07febf26c5bd059ff258c2ee1cecadcd1))
