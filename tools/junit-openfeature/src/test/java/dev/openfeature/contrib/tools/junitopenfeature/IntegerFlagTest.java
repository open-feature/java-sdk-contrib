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

class IntegerFlagTest {

    private static final String FLAG = "integer-flag";
    private static final Integer FALLBACK = -1;
    private static final Integer FLAG_VALUE = 1;
    private static final String FLAG_VALUE_STRING = "1";
    private static final Integer FLAG_VALUE_ALTERNATIVE = 0;
    private static final String FLAG_VALUE_STRING_ALTERNATIVE = "0";
    private static final String SPECIFIC_DOMAIN = "testSpecific";

    @Nested
    class SimpleConfig {

        @Test
        @Flag(name = FLAG, value = FLAG_VALUE_STRING, valueType = Integer.class)
        void existingSimpleFlagIsRetrieved() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getIntegerValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
        }

        @Test
        @IntegerFlag(name = FLAG, value = 1)
        void existingSimpleTypedFlagIsRetrieved() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getIntegerValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
        }

        @Test
        @Flag(name = FLAG, value = FLAG_VALUE_STRING, valueType = Integer.class)
        @Flag(name = FLAG + "2", value = FLAG_VALUE_STRING, valueType = Integer.class)
        @Flag(name = FLAG + "3", value = FLAG_VALUE_STRING, valueType = Integer.class)
        void multipleFlagsSimple() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getIntegerValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
            assertThat(client.getIntegerValue(FLAG + "2", FALLBACK)).isEqualTo(FLAG_VALUE);
            assertThat(client.getIntegerValue(FLAG + "3", FALLBACK)).isEqualTo(FLAG_VALUE);
        }

        @Test
        @IntegerFlag(name = FLAG, value = 1)
        @IntegerFlag(name = FLAG + "2", value = 1)
        void multipleTypedFlagsSimple() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getIntegerValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
            assertThat(client.getIntegerValue(FLAG + "2", FALLBACK)).isEqualTo(FLAG_VALUE);
        }

        @Test
        @IntegerFlag(name = FLAG, value = 1)
        @IntegerFlag(name = FLAG, value = 2)
        void duplicatedTypedFlagDoesntOverridePrevious() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getIntegerValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
        }

        @Nested
        @Flag(name = FLAG, value = FLAG_VALUE_STRING, valueType = Integer.class)
        @Flag(name = FLAG + "2", value = FLAG_VALUE_STRING, valueType = Integer.class)
        @Flag(name = FLAG + "3", value = FLAG_VALUE_STRING, valueType = Integer.class)
        @IntegerFlag(name = FLAG + "4", value = 1)
        class onClass {
            @Test
            void multipleFlagsSimple() {
                Client client = OpenFeatureAPI.getInstance().getClient();
                assertThat(client.getIntegerValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
                assertThat(client.getIntegerValue(FLAG + "2", FALLBACK)).isEqualTo(FLAG_VALUE);
                assertThat(client.getIntegerValue(FLAG + "3", FALLBACK)).isEqualTo(FLAG_VALUE);
                assertThat(client.getIntegerValue(FLAG + "4", FALLBACK)).isEqualTo(FLAG_VALUE);
            }
        }

        @Test
        @Flag(name = FLAG, value = FLAG_VALUE_STRING, valueType = Integer.class)
        @Flag(name = FLAG + "2", value = FLAG_VALUE_STRING, valueType = Integer.class)
        @Flag(name = FLAG + "3", value = FLAG_VALUE_STRING, valueType = Integer.class)
        @IntegerFlag(name = FLAG + "4", value = 1)
        void multipleDifferentFlagsSimple() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getIntegerValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
            assertThat(client.getIntegerValue(FLAG + "2", FALLBACK)).isEqualTo(FLAG_VALUE);
            assertThat(client.getIntegerValue(FLAG + "3", FALLBACK)).isEqualTo(FLAG_VALUE);
            assertThat(client.getIntegerValue(FLAG + "4", FALLBACK)).isEqualTo(FLAG_VALUE);
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 2})
        @Flag(name = FLAG, value = FLAG_VALUE_STRING, valueType = Integer.class)
        void existingSimpleFlagIsRetrievedOnParameterizedTest() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getIntegerValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 2})
        @IntegerFlag(name = FLAG, value = 1)
        void existingSimpleTypedFlagIsRetrievedOnParameterizedTest() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getIntegerValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
        }
    }

    @Nested
    @OpenFeatureDefaultDomain(SPECIFIC_DOMAIN)
    class SimpleConfigWithDefault {
        @Test
        @Flag(name = FLAG, value = FLAG_VALUE_STRING, valueType = Integer.class)
        void existingSimpleFlagIsRetrieved() {
            Client client = OpenFeatureAPI.getInstance().getClient(SPECIFIC_DOMAIN);
            assertThat(client.getIntegerValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
        }

        @Test
        @IntegerFlag(name = FLAG, value = 1)
        void existingSimpleTypedFlagIsRetrieved() {
            Client client = OpenFeatureAPI.getInstance().getClient(SPECIFIC_DOMAIN);
            assertThat(client.getIntegerValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
        }

        @Test
        @Flag(name = FLAG, value = FLAG_VALUE_STRING, valueType = Integer.class)
        @Flag(name = FLAG + "2", value = FLAG_VALUE_STRING, valueType = Integer.class)
        @Flag(name = FLAG + "3", value = FLAG_VALUE_STRING, valueType = Integer.class)
        void multipleFlagsSimple() {
            Client client = OpenFeatureAPI.getInstance().getClient(SPECIFIC_DOMAIN);
            assertThat(client.getIntegerValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
            assertThat(client.getIntegerValue(FLAG + "2", FALLBACK)).isEqualTo(FLAG_VALUE);
            assertThat(client.getIntegerValue(FLAG + "3", FALLBACK)).isEqualTo(FLAG_VALUE);
        }

        @Test
        @IntegerFlag(name = FLAG, value = 1)
        @IntegerFlag(name = FLAG + "2", value = 1)
        void multipleTypedFlagsSimple() {
            Client client = OpenFeatureAPI.getInstance().getClient(SPECIFIC_DOMAIN);
            assertThat(client.getIntegerValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
            assertThat(client.getIntegerValue(FLAG + "2", FALLBACK)).isEqualTo(FLAG_VALUE);
        }

        @Nested
        @Flag(name = FLAG, value = FLAG_VALUE_STRING, valueType = Integer.class)
        @Flag(name = FLAG + "2", value = FLAG_VALUE_STRING, valueType = Integer.class)
        @Flag(name = FLAG + "3", value = FLAG_VALUE_STRING, valueType = Integer.class)
        @IntegerFlag(name = FLAG + "4", value = 1)
        class onClass {
            @Test
            void multipleFlagsSimple() {
                Client client = OpenFeatureAPI.getInstance().getClient("testSpecific");
                assertThat(client.getIntegerValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
                assertThat(client.getIntegerValue(FLAG + "2", FALLBACK)).isEqualTo(FLAG_VALUE);
                assertThat(client.getIntegerValue(FLAG + "3", FALLBACK)).isEqualTo(FLAG_VALUE);
                assertThat(client.getIntegerValue(FLAG + "4", FALLBACK)).isEqualTo(FLAG_VALUE);
            }
        }

        @Test
        @Flag(name = FLAG, value = FLAG_VALUE_STRING, valueType = Integer.class)
        @Flag(name = FLAG + "2", value = FLAG_VALUE_STRING, valueType = Integer.class)
        @Flag(name = FLAG + "3", value = FLAG_VALUE_STRING, valueType = Integer.class)
        @IntegerFlag(name = FLAG + "4", value = 1)
        void multipleDifferentFlagsSimple() {
            Client client = OpenFeatureAPI.getInstance().getClient(SPECIFIC_DOMAIN);
            assertThat(client.getIntegerValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
            assertThat(client.getIntegerValue(FLAG + "2", FALLBACK)).isEqualTo(FLAG_VALUE);
            assertThat(client.getIntegerValue(FLAG + "3", FALLBACK)).isEqualTo(FLAG_VALUE);
            assertThat(client.getIntegerValue(FLAG + "4", FALLBACK)).isEqualTo(FLAG_VALUE);
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 2})
        @Flag(name = FLAG, value = FLAG_VALUE_STRING, valueType = Integer.class)
        void existingSimpleFlagIsRetrievedOnParameterizedTest() {
            Client client = OpenFeatureAPI.getInstance().getClient(SPECIFIC_DOMAIN);
            assertThat(client.getIntegerValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 2})
        @IntegerFlag(name = FLAG, value = 1)
        void existingSimpleTypedFlagIsRetrievedOnParameterizedTest() {
            Client client = OpenFeatureAPI.getInstance().getClient(SPECIFIC_DOMAIN);
            assertThat(client.getIntegerValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
        }
    }

    @Nested
    class ExtendedConfig {
        @Test
        @OpenFeature({@Flag(name = FLAG, value = FLAG_VALUE_STRING, valueType = Integer.class)})
        void existingFlagIsRetrieved() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getIntegerValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
        }

        @Test
        @OpenFeature(integerFlags = {@IntegerFlag(name = FLAG + "4", value = 1)})
        void existingIntegerFlagIsRetrieved() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getIntegerValue(FLAG + "4", FALLBACK)).isEqualTo(FLAG_VALUE);
        }

        @Test
        @OpenFeature(@Flag(name = FLAG, value = "truesadf"))
        void strangeFlagValue() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getIntegerValue(FLAG, FALLBACK)).isEqualTo(FALLBACK);
        }

        @Test
        @OpenFeature(@Flag(name = FLAG, value = FLAG_VALUE_STRING, valueType = Integer.class))
        void nonExistingFlagIsFallbacked() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getIntegerValue("nonSetFlag", FALLBACK)).isEqualTo(FALLBACK);
        }

        @Test
        @OpenFeature(integerFlags = @IntegerFlag(name = FLAG, value = 1))
        void nonExistingTypedFlagIsFallbacked() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getIntegerValue("nonSetFlag", FALLBACK)).isEqualTo(FALLBACK);
        }

        @Test
        @OpenFeature(
                value = {
                    @Flag(name = FLAG, value = FLAG_VALUE_STRING, valueType = Integer.class),
                    @Flag(name = FLAG + "2", value = FLAG_VALUE_STRING, valueType = Integer.class),
                    @Flag(name = FLAG + "3", value = FLAG_VALUE_STRING, valueType = Integer.class),
                })
        void multipleFlags() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getIntegerValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
            assertThat(client.getIntegerValue(FLAG + "2", FALLBACK)).isEqualTo(FLAG_VALUE);
            assertThat(client.getIntegerValue(FLAG + "3", FALLBACK)).isEqualTo(FLAG_VALUE);
        }

        @Test
        @OpenFeature(
                integerFlags = {
                    @IntegerFlag(name = FLAG + "4", value = 1),
                    @IntegerFlag(name = FLAG + "5", value = 1),
                })
        void multipleTypedFlags() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getIntegerValue(FLAG + "4", FALLBACK)).isEqualTo(FLAG_VALUE);
            assertThat(client.getIntegerValue(FLAG + "5", FALLBACK)).isEqualTo(FLAG_VALUE);
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 2})
        @OpenFeature({@Flag(name = FLAG, value = FLAG_VALUE_STRING, valueType = Integer.class)})
        void existingSimpleFlagIsRetrievedOnParameterizedTest() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getIntegerValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 2})
        @OpenFeature(integerFlags = {@IntegerFlag(name = FLAG + "4", value = 1)})
        void existingTypedFlagIsRetrievedOnParameterizedTest() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getIntegerValue(FLAG + "4", FALLBACK)).isEqualTo(FLAG_VALUE);
        }

        @Nested
        @OpenFeature(
                value = {
                    @Flag(name = FLAG, value = FLAG_VALUE_STRING, valueType = Integer.class),
                    @Flag(name = FLAG + "2", value = FLAG_VALUE_STRING_ALTERNATIVE, valueType = Integer.class),
                },
                integerFlags = {@IntegerFlag(name = FLAG + "4", value = 1), @IntegerFlag(name = FLAG + "5", value = 0)})
        class MultipleFlags {
            @Test
            @OpenFeature(
                    value = {
                        @Flag(name = FLAG + "2", value = FLAG_VALUE_STRING, valueType = Integer.class),
                        @Flag(name = FLAG + "3", value = FLAG_VALUE_STRING, valueType = Integer.class),
                    },
                    integerFlags = {
                        @IntegerFlag(name = FLAG + "5", value = 1),
                        @IntegerFlag(name = FLAG + "6", value = 1)
                    })
            void multipleFlags() {
                Client client = OpenFeatureAPI.getInstance().getClient();
                assertThat(client.getIntegerValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
                assertThat(client.getIntegerValue(FLAG + "2", FALLBACK)).isEqualTo(FLAG_VALUE);
                assertThat(client.getIntegerValue(FLAG + "3", FALLBACK)).isEqualTo(FLAG_VALUE);
                assertThat(client.getIntegerValue(FLAG + "4", FALLBACK)).isEqualTo(FLAG_VALUE);
                assertThat(client.getIntegerValue(FLAG + "5", FALLBACK)).isEqualTo(FLAG_VALUE);
                assertThat(client.getIntegerValue(FLAG + "6", FALLBACK)).isEqualTo(FLAG_VALUE);
            }

            @Test
            @OpenFeature(
                    domain = SPECIFIC_DOMAIN,
                    value = {
                        @Flag(name = FLAG + "2", value = FLAG_VALUE_STRING, valueType = Integer.class),
                        @Flag(name = FLAG + "3", value = FLAG_VALUE_STRING, valueType = Integer.class),
                    },
                    integerFlags = {
                        @IntegerFlag(name = FLAG + "5", value = 1),
                        @IntegerFlag(name = FLAG + "6", value = 1)
                    })
            void multipleFlagsOnMultipleDomains() {
                Client client = OpenFeatureAPI.getInstance().getClient();
                assertThat(client.getIntegerValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
                assertThat(client.getIntegerValue(FLAG + "2", FALLBACK)).isEqualTo(FLAG_VALUE_ALTERNATIVE);
                assertThat(client.getIntegerValue(FLAG + "3", FALLBACK)).isEqualTo(FALLBACK);
                assertThat(client.getIntegerValue(FLAG + "4", FALLBACK)).isEqualTo(FLAG_VALUE);
                assertThat(client.getIntegerValue(FLAG + "5", FALLBACK)).isEqualTo(FLAG_VALUE_ALTERNATIVE);

                Client testSpecific = OpenFeatureAPI.getInstance().getClient(SPECIFIC_DOMAIN);
                assertThat(testSpecific.getIntegerValue(FLAG, FALLBACK)).isEqualTo(FALLBACK);
                assertThat(testSpecific.getIntegerValue(FLAG + "2", FALLBACK)).isEqualTo(FLAG_VALUE);
                assertThat(testSpecific.getIntegerValue(FLAG + "3", FALLBACK)).isEqualTo(FLAG_VALUE);
                assertThat(testSpecific.getIntegerValue(FLAG + "4", FALLBACK)).isEqualTo(FALLBACK);
                assertThat(testSpecific.getIntegerValue(FLAG + "5", FALLBACK)).isEqualTo(FLAG_VALUE);
                assertThat(testSpecific.getIntegerValue(FLAG + "6", FALLBACK)).isEqualTo(FLAG_VALUE);
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
                                    message("Flag with name integer-flag already exists. "
                                            + "There shouldn't be @Flag and @IntegerFlag with the same name!"))));
        }

        @Nested
        @Disabled
        class ConfigWithException {

            @Test
            @Flag(name = FLAG, value = FLAG_VALUE_STRING)
            @IntegerFlag(name = FLAG, value = 1)
            void simpleConfigDuplicateFlags() {
                // expect exception in OpenFeatureExtension
            }

            @Test
            @OpenFeature(
                    value = {@Flag(name = FLAG, value = FLAG_VALUE_STRING)},
                    integerFlags = {@IntegerFlag(name = FLAG, value = 1)})
            void extendedConfigDuplicateFlags() {
                // expect exception in OpenFeatureExtension
            }
        }
    }
}
