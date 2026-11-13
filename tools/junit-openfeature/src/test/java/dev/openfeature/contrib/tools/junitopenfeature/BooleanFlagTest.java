package dev.openfeature.contrib.tools.junitopenfeature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.testkit.engine.EventConditions.event;
import static org.junit.platform.testkit.engine.EventConditions.finishedWithFailure;
import static org.junit.platform.testkit.engine.TestExecutionResultConditions.instanceOf;
import static org.junit.platform.testkit.engine.TestExecutionResultConditions.message;

import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.OpenFeatureAPI;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Events;

class BooleanFlagTest {

    private static final String FLAG = "boolean-flag";

    @Nested
    class SimpleConfig {

        @Nested
        @Flag(name = FLAG, value = "true")
        @Flag(name = FLAG + "2", value = "true")
        @Flag(name = FLAG + "3", value = "true")
        @BooleanFlag(name = FLAG + "4", value = true)
        class onClass {
            @Test
            void multipleFlagsSimple() {
                Client client = OpenFeatureAPI.getInstance().getClient();
                assertThat(client.getBooleanValue(FLAG, false)).isTrue();
                assertThat(client.getBooleanValue(FLAG + "2", false)).isTrue();
                assertThat(client.getBooleanValue(FLAG + "3", false)).isTrue();
                assertThat(client.getBooleanValue(FLAG + "4", false)).isTrue();
            }
        }

        @Test
        @Flag(name = FLAG, value = "true")
        void existingSimpleFlagIsRetrieved() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getBooleanValue(FLAG, false)).isTrue();
        }

        @Test
        @BooleanFlag(name = FLAG, value = true)
        void existingSimpleTypedFlagIsRetrieved() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getBooleanValue(FLAG, false)).isTrue();
        }

        @Test
        @Flag(name = FLAG, value = "true")
        @Flag(name = FLAG + "2", value = "true")
        @Flag(name = FLAG + "3", value = "true")
        void multipleFlagsSimple() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getBooleanValue(FLAG, false)).isTrue();
            assertThat(client.getBooleanValue(FLAG + "2", false)).isTrue();
            assertThat(client.getBooleanValue(FLAG + "3", false)).isTrue();
        }

        @Test
        @BooleanFlag(name = FLAG, value = true)
        @BooleanFlag(name = FLAG + "2", value = true)
        void multipleTypedFlagsSimple() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getBooleanValue(FLAG, false)).isTrue();
            assertThat(client.getBooleanValue(FLAG + "2", false)).isTrue();
        }

        @Test
        @BooleanFlag(name = FLAG, value = true)
        @BooleanFlag(name = FLAG, value = false)
        void duplicatedTypedFlagDoesntOverridePrevious() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getBooleanValue(FLAG, false)).isTrue();
        }

        @Test
        @Flag(name = FLAG, value = "true")
        @Flag(name = FLAG + "2", value = "true")
        @Flag(name = FLAG + "3", value = "true")
        @BooleanFlag(name = FLAG + "4", value = true)
        void multipleDifferentFlagsSimple() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getBooleanValue(FLAG, false)).isTrue();
            assertThat(client.getBooleanValue(FLAG + "2", false)).isTrue();
            assertThat(client.getBooleanValue(FLAG + "3", false)).isTrue();
            assertThat(client.getBooleanValue(FLAG + "4", false)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 2})
        @Flag(name = FLAG, value = "true")
        void existingSimpleFlagIsRetrievedOnParameterizedTest() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getBooleanValue(FLAG, false)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 2})
        @BooleanFlag(name = FLAG, value = true)
        void existingSimpleTypedFlagIsRetrievedOnParameterizedTest() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getBooleanValue(FLAG, false)).isTrue();
        }
    }

    @Nested
    @OpenFeatureDefaultDomain("testSpecific")
    class SimpleConfigWithDefault {
        @Nested
        @Flag(name = FLAG, value = "true")
        @Flag(name = FLAG + "2", value = "true")
        @Flag(name = FLAG + "3", value = "true")
        @BooleanFlag(name = FLAG + "4", value = true)
        class onClass {
            @Test
            void multipleFlagsSimple() {
                Client client = OpenFeatureAPI.getInstance().getClient("testSpecific");
                assertThat(client.getBooleanValue(FLAG, false)).isTrue();
                assertThat(client.getBooleanValue(FLAG + "2", false)).isTrue();
                assertThat(client.getBooleanValue(FLAG + "3", false)).isTrue();
                assertThat(client.getBooleanValue(FLAG + "4", false)).isTrue();
            }
        }

        @Test
        @Flag(name = FLAG, value = "true")
        void existingSimpleFlagIsRetrieved() {
            Client client = OpenFeatureAPI.getInstance().getClient("testSpecific");
            assertThat(client.getBooleanValue(FLAG, false)).isTrue();
        }

        @Test
        @BooleanFlag(name = FLAG, value = true)
        void existingSimpleTypedFlagIsRetrieved() {
            Client client = OpenFeatureAPI.getInstance().getClient("testSpecific");
            assertThat(client.getBooleanValue(FLAG, false)).isTrue();
        }

        @Test
        @Flag(name = FLAG, value = "true")
        @Flag(name = FLAG + "2", value = "true")
        @Flag(name = FLAG + "3", value = "true")
        void multipleFlagsSimple() {
            Client client = OpenFeatureAPI.getInstance().getClient("testSpecific");
            assertThat(client.getBooleanValue(FLAG, false)).isTrue();
            assertThat(client.getBooleanValue(FLAG + "2", false)).isTrue();
            assertThat(client.getBooleanValue(FLAG + "3", false)).isTrue();
        }

        @Test
        @BooleanFlag(name = FLAG, value = true)
        @BooleanFlag(name = FLAG + "2", value = true)
        void multipleTypedFlagsSimple() {
            Client client = OpenFeatureAPI.getInstance().getClient("testSpecific");
            assertThat(client.getBooleanValue(FLAG, false)).isTrue();
            assertThat(client.getBooleanValue(FLAG + "2", false)).isTrue();
        }

        @Test
        @Flag(name = FLAG, value = "true")
        @Flag(name = FLAG + "2", value = "true")
        @Flag(name = FLAG + "3", value = "true")
        @BooleanFlag(name = FLAG + "4", value = true)
        void multipleDifferentFlagsSimple() {
            Client client = OpenFeatureAPI.getInstance().getClient("testSpecific");
            assertThat(client.getBooleanValue(FLAG, false)).isTrue();
            assertThat(client.getBooleanValue(FLAG + "2", false)).isTrue();
            assertThat(client.getBooleanValue(FLAG + "3", false)).isTrue();
            assertThat(client.getBooleanValue(FLAG + "4", false)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 2})
        @Flag(name = FLAG, value = "true")
        void existingSimpleFlagIsRetrievedOnParameterizedTest() {
            Client client = OpenFeatureAPI.getInstance().getClient("testSpecific");
            assertThat(client.getBooleanValue(FLAG, false)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 2})
        @BooleanFlag(name = FLAG, value = true)
        void existingSimpleTypedFlagIsRetrievedOnParameterizedTest() {
            Client client = OpenFeatureAPI.getInstance().getClient("testSpecific");
            assertThat(client.getBooleanValue(FLAG, false)).isTrue();
        }
    }

    @Nested
    class ExtendedConfig {
        @Test
        @OpenFeature({@Flag(name = FLAG, value = "true")})
        void existingFlagIsRetrieved() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getBooleanValue(FLAG, false)).isTrue();
        }

        @Test
        @OpenFeature(booleanFlags = {@BooleanFlag(name = FLAG + "4", value = true)})
        void existingBooleanFlagIsRetrieved() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getBooleanValue(FLAG + "4", false)).isTrue();
        }

        @Test
        @OpenFeature(@Flag(name = FLAG, value = "truesadf"))
        void strangeFlagValue() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getBooleanValue(FLAG, false)).isFalse();
        }

        @Test
        @OpenFeature(@Flag(name = FLAG, value = "true"))
        void nonExistingFlagIsFallbacked() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getBooleanValue("nonSetFlag", false)).isFalse();
        }

        @Test
        @OpenFeature(booleanFlags = @BooleanFlag(name = FLAG, value = true))
        void nonExistingTypedFlagIsFallbacked() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getBooleanValue("nonSetFlag", false)).isFalse();
        }

        @Test
        @OpenFeature({
            @Flag(name = FLAG, value = "true"),
            @Flag(name = FLAG + "2", value = "true"),
            @Flag(name = FLAG + "3", value = "true"),
        })
        void multipleFlags() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getBooleanValue(FLAG, false)).isTrue();
            assertThat(client.getBooleanValue(FLAG + "2", false)).isTrue();
            assertThat(client.getBooleanValue(FLAG + "3", false)).isTrue();
        }

        @Test
        @OpenFeature(
                booleanFlags = {
                    @BooleanFlag(name = FLAG + "4", value = true),
                    @BooleanFlag(name = FLAG + "5", value = true),
                })
        void multipleTypedFlags() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getBooleanValue(FLAG + "4", false)).isTrue();
            assertThat(client.getBooleanValue(FLAG + "5", false)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 2})
        @OpenFeature({@Flag(name = FLAG, value = "true")})
        void existingFlagIsRetrievedOnParameterizedTest() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getBooleanValue(FLAG, false)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 2})
        @OpenFeature(booleanFlags = {@BooleanFlag(name = FLAG + "4", value = true)})
        void existingTypedFlagIsRetrievedOnParameterizedTest() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getBooleanValue(FLAG + "4", false)).isTrue();
        }

        @Nested
        @OpenFeature(
                value = {
                    @Flag(name = FLAG, value = "true"),
                    @Flag(name = FLAG + "2", value = "false"),
                },
                booleanFlags = {
                    @BooleanFlag(name = FLAG + "4", value = true),
                    @BooleanFlag(name = FLAG + "5", value = false)
                })
        class MultipleFlags {
            @Test
            @OpenFeature(
                    value = {
                        @Flag(name = FLAG + "2", value = "true"),
                        @Flag(name = FLAG + "3", value = "true"),
                    },
                    booleanFlags = {
                        @BooleanFlag(name = FLAG + "5", value = true),
                        @BooleanFlag(name = FLAG + "6", value = true)
                    })
            void multipleFlags() {
                Client client = OpenFeatureAPI.getInstance().getClient();
                assertThat(client.getBooleanValue(FLAG, false)).isTrue();
                assertThat(client.getBooleanValue(FLAG + "2", false)).isTrue();
                assertThat(client.getBooleanValue(FLAG + "3", false)).isTrue();
                assertThat(client.getBooleanValue(FLAG + "4", false)).isTrue();
                assertThat(client.getBooleanValue(FLAG + "5", false)).isTrue();
                assertThat(client.getBooleanValue(FLAG + "6", false)).isTrue();
            }

            @Test
            @OpenFeature(
                    domain = "testSpecific",
                    value = {
                        @Flag(name = FLAG + "2", value = "true"),
                        @Flag(name = FLAG + "3", value = "true"),
                    },
                    booleanFlags = {
                        @BooleanFlag(name = FLAG + "5", value = true),
                        @BooleanFlag(name = FLAG + "6", value = true)
                    })
            void multipleFlagsOnMultipleDomains() {
                Client client = OpenFeatureAPI.getInstance().getClient();
                assertThat(client.getBooleanValue(FLAG, false)).isTrue();
                assertThat(client.getBooleanValue(FLAG + "2", true)).isFalse();
                assertThat(client.getBooleanValue(FLAG + "3", false)).isFalse();
                assertThat(client.getBooleanValue(FLAG + "4", false)).isTrue();
                assertThat(client.getBooleanValue(FLAG + "5", false)).isFalse();

                Client testSpecific = OpenFeatureAPI.getInstance().getClient("testSpecific");
                assertThat(testSpecific.getBooleanValue(FLAG, false)).isFalse();
                assertThat(testSpecific.getBooleanValue(FLAG + "2", false)).isTrue();
                assertThat(testSpecific.getBooleanValue(FLAG + "3", false)).isTrue();
                assertThat(testSpecific.getBooleanValue(FLAG + "4", false)).isFalse();
                assertThat(testSpecific.getBooleanValue(FLAG + "5", false)).isTrue();
                assertThat(testSpecific.getBooleanValue(FLAG + "6", false)).isTrue();
            }
        }
    }

    @Nested
    class DifferentFlagsWithTheSameNameException {

        @Test
        void differentFlagTypesWithTheSameNameThrowsException() {
            Events events = EngineTestKit.engine("junit-jupiter")
                    .configurationParameter("junit.jupiter.conditions.deactivate", "org.junit.*DisabledCondition")
                    .selectors(selectClass(ConfigWithException.class))
                    .execute()
                    .testEvents()
                    .failed();

            events.assertThatEvents()
                    .haveExactly(
                            2,
                            event(finishedWithFailure(
                                    instanceOf(IllegalArgumentException.class),
                                    message("Flag with name boolean-flag already exists. "
                                            + "There shouldn't be @Flag and @BooleanFlag with the same name!"))));
        }

        @Nested
        @Disabled
        class ConfigWithException {

            @Test
            @Flag(name = FLAG, value = "true")
            @BooleanFlag(name = FLAG, value = true)
            void simpleConfigDuplicateFlags() {
                // expect exception in OpenFeatureExtension
            }

            @Test
            @OpenFeature(
                    value = {@Flag(name = FLAG, value = "true")},
                    booleanFlags = {@BooleanFlag(name = FLAG, value = true)})
            void extendedConfigDuplicateFlags() {
                // expect exception in OpenFeatureExtension
            }
        }
    }
}
