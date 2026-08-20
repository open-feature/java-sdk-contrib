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

class StringFlagTest {

    private static final String FLAG = "string-flag";
    private static final String FALLBACK = "fallback";
    private static final String FLAG_VALUE = "true";
    private static final String FLAG_VALUE_ALTERNATIVE = "false";
    private static final String SPECIFIC_DOMAIN = "testSpecific";

    @Nested
    class SimpleConfig {

        @Nested
        @Flag(name = FLAG, value = FLAG_VALUE, valueType = String.class)
        @Flag(name = FLAG + "2", value = FLAG_VALUE, valueType = String.class)
        @Flag(name = FLAG + "3", value = FLAG_VALUE, valueType = String.class)
        @StringFlag(name = FLAG + "4", value = FLAG_VALUE)
        class onClass {
            @Test
            void multipleFlagsSimple() {
                Client client = OpenFeatureAPI.getInstance().getClient();
                assertThat(client.getStringValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
                assertThat(client.getStringValue(FLAG + "2", FALLBACK)).isEqualTo(FLAG_VALUE);
                assertThat(client.getStringValue(FLAG + "3", FALLBACK)).isEqualTo(FLAG_VALUE);
                assertThat(client.getStringValue(FLAG + "4", FALLBACK)).isEqualTo(FLAG_VALUE);
            }
        }

        @Test
        @Flag(name = FLAG, value = FLAG_VALUE, valueType = String.class)
        void existingSimpleFlagIsRetrieved() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getStringValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
        }

        @Test
        @StringFlag(name = FLAG, value = FLAG_VALUE)
        void existingSimpleTypedFlagIsRetrieved() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getStringValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
        }

        @Test
        @Flag(name = FLAG, value = FLAG_VALUE, valueType = String.class)
        @Flag(name = FLAG + "2", value = FLAG_VALUE, valueType = String.class)
        @Flag(name = FLAG + "3", value = FLAG_VALUE, valueType = String.class)
        void multipleFlagsSimple() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getStringValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
            assertThat(client.getStringValue(FLAG + "2", FALLBACK)).isEqualTo(FLAG_VALUE);
            assertThat(client.getStringValue(FLAG + "3", FALLBACK)).isEqualTo(FLAG_VALUE);
        }

        @Test
        @StringFlag(name = FLAG, value = FLAG_VALUE)
        @StringFlag(name = FLAG + "2", value = FLAG_VALUE)
        void multipleTypedFlagsSimple() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getStringValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
            assertThat(client.getStringValue(FLAG + "2", FALLBACK)).isEqualTo(FLAG_VALUE);
        }

        @Test
        @StringFlag(name = FLAG, value = FLAG_VALUE)
        @StringFlag(name = FLAG, value = FLAG_VALUE_ALTERNATIVE)
        void duplicatedTypedFlagDoesntOverridePrevious() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getStringValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
        }

        @Test
        @Flag(name = FLAG, value = FLAG_VALUE, valueType = String.class)
        @Flag(name = FLAG + "2", value = FLAG_VALUE, valueType = String.class)
        @Flag(name = FLAG + "3", value = FLAG_VALUE, valueType = String.class)
        @StringFlag(name = FLAG + "4", value = FLAG_VALUE)
        void multipleDifferentFlagsSimple() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getStringValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
            assertThat(client.getStringValue(FLAG + "2", FALLBACK)).isEqualTo(FLAG_VALUE);
            assertThat(client.getStringValue(FLAG + "3", FALLBACK)).isEqualTo(FLAG_VALUE);
            assertThat(client.getStringValue(FLAG + "4", FALLBACK)).isEqualTo(FLAG_VALUE);
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 2})
        @Flag(name = FLAG, value = FLAG_VALUE, valueType = String.class)
        void existingSimpleFlagIsRetrievedOnParameterizedTest() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getStringValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 2})
        @StringFlag(name = FLAG, value = FLAG_VALUE)
        void existingSimpleTypedFlagIsRetrievedOnParameterizedTest() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getStringValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
        }
    }

    @Nested
    @OpenFeatureDefaultDomain(SPECIFIC_DOMAIN)
    class SimpleConfigWithDefault {
        @Nested
        @Flag(name = FLAG, value = FLAG_VALUE, valueType = String.class)
        @Flag(name = FLAG + "2", value = FLAG_VALUE, valueType = String.class)
        @Flag(name = FLAG + "3", value = FLAG_VALUE, valueType = String.class)
        @StringFlag(name = FLAG + "4", value = FLAG_VALUE)
        class onClass {
            @Test
            void multipleFlagsSimple() {
                Client client = OpenFeatureAPI.getInstance().getClient("testSpecific");
                assertThat(client.getStringValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
                assertThat(client.getStringValue(FLAG + "2", FALLBACK)).isEqualTo(FLAG_VALUE);
                assertThat(client.getStringValue(FLAG + "3", FALLBACK)).isEqualTo(FLAG_VALUE);
                assertThat(client.getStringValue(FLAG + "4", FALLBACK)).isEqualTo(FLAG_VALUE);
            }
        }

        @Test
        @Flag(name = FLAG, value = FLAG_VALUE, valueType = String.class)
        void existingSimpleFlagIsRetrieved() {
            Client client = OpenFeatureAPI.getInstance().getClient(SPECIFIC_DOMAIN);
            assertThat(client.getStringValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
        }

        @Test
        @StringFlag(name = FLAG, value = FLAG_VALUE)
        void existingSimpleTypedFlagIsRetrieved() {
            Client client = OpenFeatureAPI.getInstance().getClient(SPECIFIC_DOMAIN);
            assertThat(client.getStringValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
        }

        @Test
        @Flag(name = FLAG, value = FLAG_VALUE, valueType = String.class)
        @Flag(name = FLAG + "2", value = FLAG_VALUE, valueType = String.class)
        @Flag(name = FLAG + "3", value = FLAG_VALUE, valueType = String.class)
        void multipleFlagsSimple() {
            Client client = OpenFeatureAPI.getInstance().getClient(SPECIFIC_DOMAIN);
            assertThat(client.getStringValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
            assertThat(client.getStringValue(FLAG + "2", FALLBACK)).isEqualTo(FLAG_VALUE);
            assertThat(client.getStringValue(FLAG + "3", FALLBACK)).isEqualTo(FLAG_VALUE);
        }

        @Test
        @StringFlag(name = FLAG, value = FLAG_VALUE)
        @StringFlag(name = FLAG + "2", value = FLAG_VALUE)
        void multipleTypedFlagsSimple() {
            Client client = OpenFeatureAPI.getInstance().getClient(SPECIFIC_DOMAIN);
            assertThat(client.getStringValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
            assertThat(client.getStringValue(FLAG + "2", FALLBACK)).isEqualTo(FLAG_VALUE);
        }

        @Test
        @Flag(name = FLAG, value = FLAG_VALUE, valueType = String.class)
        @Flag(name = FLAG + "2", value = FLAG_VALUE, valueType = String.class)
        @Flag(name = FLAG + "3", value = FLAG_VALUE, valueType = String.class)
        @StringFlag(name = FLAG + "4", value = FLAG_VALUE)
        void multipleDifferentFlagsSimple() {
            Client client = OpenFeatureAPI.getInstance().getClient(SPECIFIC_DOMAIN);
            assertThat(client.getStringValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
            assertThat(client.getStringValue(FLAG + "2", FALLBACK)).isEqualTo(FLAG_VALUE);
            assertThat(client.getStringValue(FLAG + "3", FALLBACK)).isEqualTo(FLAG_VALUE);
            assertThat(client.getStringValue(FLAG + "4", FALLBACK)).isEqualTo(FLAG_VALUE);
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 2})
        @Flag(name = FLAG, value = FLAG_VALUE, valueType = String.class)
        void existingSimpleFlagIsRetrievedOnParameterizedTest() {
            Client client = OpenFeatureAPI.getInstance().getClient(SPECIFIC_DOMAIN);
            assertThat(client.getStringValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 2})
        @StringFlag(name = FLAG, value = FLAG_VALUE)
        void existingSimpleTypedFlagIsRetrievedOnParameterizedTest() {
            Client client = OpenFeatureAPI.getInstance().getClient(SPECIFIC_DOMAIN);
            assertThat(client.getStringValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
        }
    }

    @Nested
    class ExtendedConfig {
        @Test
        @OpenFeature({@Flag(name = FLAG, value = FLAG_VALUE, valueType = String.class)})
        void existingFlagIsRetrieved() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getStringValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
        }

        @Test
        @OpenFeature(stringFlags = {@StringFlag(name = FLAG + "4", value = FLAG_VALUE)})
        void existingStringFlagIsRetrieved() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getStringValue(FLAG + "4", FALLBACK)).isEqualTo(FLAG_VALUE);
        }

        @Test
        @OpenFeature(@Flag(name = FLAG, value = "truesadf"))
        void strangeFlagValue() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getStringValue(FLAG, FALLBACK)).isEqualTo(FALLBACK);
        }

        @Test
        @OpenFeature(@Flag(name = FLAG, value = FLAG_VALUE, valueType = String.class))
        void nonExistingFlagIsFallbacked() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getStringValue("nonSetFlag", FALLBACK)).isEqualTo(FALLBACK);
        }

        @Test
        @OpenFeature(stringFlags = @StringFlag(name = FLAG, value = FLAG_VALUE))
        void nonExistingTypedFlagIsFallbacked() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getStringValue("nonSetFlag", FALLBACK)).isEqualTo(FALLBACK);
        }

        @Test
        @OpenFeature({
            @Flag(name = FLAG, value = FLAG_VALUE, valueType = String.class),
            @Flag(name = FLAG + "2", value = FLAG_VALUE, valueType = String.class),
            @Flag(name = FLAG + "3", value = FLAG_VALUE, valueType = String.class),
        })
        void multipleFlags() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getStringValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
            assertThat(client.getStringValue(FLAG + "2", FALLBACK)).isEqualTo(FLAG_VALUE);
            assertThat(client.getStringValue(FLAG + "3", FALLBACK)).isEqualTo(FLAG_VALUE);
        }

        @Test
        @OpenFeature(
                stringFlags = {
                    @StringFlag(name = FLAG + "4", value = FLAG_VALUE),
                    @StringFlag(name = FLAG + "5", value = FLAG_VALUE),
                })
        void multipleTypedFlags() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getStringValue(FLAG + "4", FALLBACK)).isEqualTo(FLAG_VALUE);
            assertThat(client.getStringValue(FLAG + "5", FALLBACK)).isEqualTo(FLAG_VALUE);
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 2})
        @OpenFeature({@Flag(name = FLAG, value = FLAG_VALUE, valueType = String.class)})
        void existingSimpleFlagIsRetrievedOnParameterizedTest() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getStringValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 2})
        @OpenFeature(stringFlags = {@StringFlag(name = FLAG + "4", value = FLAG_VALUE)})
        void existingTypedFlagIsRetrievedOnParameterizedTest() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getStringValue(FLAG + "4", FALLBACK)).isEqualTo(FLAG_VALUE);
        }

        @Nested
        @OpenFeature(
                value = {
                    @Flag(name = FLAG, value = FLAG_VALUE, valueType = String.class),
                    @Flag(name = FLAG + "2", value = FLAG_VALUE_ALTERNATIVE, valueType = String.class),
                },
                stringFlags = {
                    @StringFlag(name = FLAG + "4", value = FLAG_VALUE),
                    @StringFlag(name = FLAG + "5", value = FLAG_VALUE_ALTERNATIVE)
                })
        class MultipleFlags {
            @Test
            @OpenFeature(
                    value = {
                        @Flag(name = FLAG + "2", value = FLAG_VALUE, valueType = String.class),
                        @Flag(name = FLAG + "3", value = FLAG_VALUE, valueType = String.class),
                    },
                    stringFlags = {
                        @StringFlag(name = FLAG + "5", value = FLAG_VALUE),
                        @StringFlag(name = FLAG + "6", value = FLAG_VALUE)
                    })
            void multipleFlags() {
                Client client = OpenFeatureAPI.getInstance().getClient();
                assertThat(client.getStringValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
                assertThat(client.getStringValue(FLAG + "2", FALLBACK)).isEqualTo(FLAG_VALUE);
                assertThat(client.getStringValue(FLAG + "3", FALLBACK)).isEqualTo(FLAG_VALUE);
                assertThat(client.getStringValue(FLAG + "4", FALLBACK)).isEqualTo(FLAG_VALUE);
                assertThat(client.getStringValue(FLAG + "5", FALLBACK)).isEqualTo(FLAG_VALUE);
                assertThat(client.getStringValue(FLAG + "6", FALLBACK)).isEqualTo(FLAG_VALUE);
            }

            @Test
            @OpenFeature(
                    domain = SPECIFIC_DOMAIN,
                    value = {
                        @Flag(name = FLAG + "2", value = FLAG_VALUE, valueType = String.class),
                        @Flag(name = FLAG + "3", value = FLAG_VALUE, valueType = String.class),
                    },
                    stringFlags = {
                        @StringFlag(name = FLAG + "5", value = FLAG_VALUE),
                        @StringFlag(name = FLAG + "6", value = FLAG_VALUE)
                    })
            void multipleFlagsOnMultipleDomains() {
                Client client = OpenFeatureAPI.getInstance().getClient();
                assertThat(client.getStringValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
                assertThat(client.getStringValue(FLAG + "2", FALLBACK)).isEqualTo(FLAG_VALUE_ALTERNATIVE);
                assertThat(client.getStringValue(FLAG + "3", FALLBACK)).isEqualTo(FALLBACK);
                assertThat(client.getStringValue(FLAG + "4", FALLBACK)).isEqualTo(FLAG_VALUE);
                assertThat(client.getStringValue(FLAG + "5", FALLBACK)).isEqualTo(FLAG_VALUE_ALTERNATIVE);

                Client testSpecific = OpenFeatureAPI.getInstance().getClient(SPECIFIC_DOMAIN);
                assertThat(testSpecific.getStringValue(FLAG, FALLBACK)).isEqualTo(FALLBACK);
                assertThat(testSpecific.getStringValue(FLAG + "2", FALLBACK)).isEqualTo(FLAG_VALUE);
                assertThat(testSpecific.getStringValue(FLAG + "3", FALLBACK)).isEqualTo(FLAG_VALUE);
                assertThat(testSpecific.getStringValue(FLAG + "4", FALLBACK)).isEqualTo(FALLBACK);
                assertThat(testSpecific.getStringValue(FLAG + "5", FALLBACK)).isEqualTo(FLAG_VALUE);
                assertThat(testSpecific.getStringValue(FLAG + "6", FALLBACK)).isEqualTo(FLAG_VALUE);
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
                                    message("Flag with name string-flag already exists. "
                                            + "There shouldn't be @Flag and @StringFlag with the same name!"))));
        }

        @Nested
        @Disabled
        class ConfigWithException {

            @Test
            @Flag(name = FLAG, value = FLAG_VALUE)
            @StringFlag(name = FLAG, value = FLAG_VALUE)
            void simpleConfigDuplicateFlags() {
                // expect exception in OpenFeatureExtension
            }

            @Test
            @OpenFeature(
                    value = {@Flag(name = FLAG, value = FLAG_VALUE)},
                    stringFlags = {@StringFlag(name = FLAG, value = FLAG_VALUE)})
            void extendedConfigDuplicateFlags() {
                // expect exception in OpenFeatureExtension
            }
        }
    }
}
