package dev.openfeature.contrib.tools.junitopenfeature;

import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.OpenFeatureAPI;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.assertj.core.api.Assertions.assertThat;

@Execution(ExecutionMode.SAME_THREAD)
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
        @Flag(name = FLAG, value = FLAG_VALUE_STRING, valueType = Integer.class)
        @Flag(name = FLAG + "2", value = FLAG_VALUE_STRING, valueType = Integer.class)
        @Flag(name = FLAG + "3", value = FLAG_VALUE_STRING, valueType = Integer.class)
        void multipleFlagsSimple() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getIntegerValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
            assertThat(client.getIntegerValue(FLAG + "2", FALLBACK)).isEqualTo(FLAG_VALUE);
            assertThat(client.getIntegerValue(FLAG + "3", FALLBACK)).isEqualTo(FLAG_VALUE);
        }

        @Nested
        @Flag(name = FLAG, value = FLAG_VALUE_STRING  , valueType = Integer.class)
        @Flag(name = FLAG + "2", value = FLAG_VALUE_STRING, valueType = Integer.class)
        @Flag(name = FLAG + "3", value = FLAG_VALUE_STRING, valueType = Integer.class)
        class onClass {
            @Test
            void multipleFlagsSimple() {
                Client client = OpenFeatureAPI.getInstance().getClient();
                assertThat(client.getIntegerValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
                assertThat(client.getIntegerValue(FLAG + "2", FALLBACK)).isEqualTo(FLAG_VALUE);
                assertThat(client.getIntegerValue(FLAG + "3", FALLBACK)).isEqualTo(FLAG_VALUE);
            }
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
        @Flag(name = FLAG, value = FLAG_VALUE_STRING, valueType = Integer.class)
        @Flag(name = FLAG + "2", value = FLAG_VALUE_STRING, valueType = Integer.class)
        @Flag(name = FLAG + "3", value = FLAG_VALUE_STRING, valueType = Integer.class)
        void multipleFlagsSimple() {
            Client client = OpenFeatureAPI.getInstance().getClient(SPECIFIC_DOMAIN);
            assertThat(client.getIntegerValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
            assertThat(client.getIntegerValue(FLAG + "2", FALLBACK)).isEqualTo(FLAG_VALUE);
            assertThat(client.getIntegerValue(FLAG + "3", FALLBACK)).isEqualTo(FLAG_VALUE);
        }

        @Nested
        @Flag(name = FLAG, value = FLAG_VALUE_STRING, valueType = Integer.class)
        @Flag(name = FLAG + "2", value = FLAG_VALUE_STRING, valueType = Integer.class)
        @Flag(name = FLAG + "3", value = FLAG_VALUE_STRING, valueType = Integer.class)
        class onClass {
            @Test
            void multipleFlagsSimple() {
                Client client = OpenFeatureAPI.getInstance().getClient("testSpecific");
                assertThat(client.getIntegerValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
                assertThat(client.getIntegerValue(FLAG + "2", FALLBACK)).isEqualTo(FLAG_VALUE);
                assertThat(client.getIntegerValue(FLAG + "3", FALLBACK)).isEqualTo(FLAG_VALUE);
            }
        }
    }

    @Nested
    class ExtendedConfig {
        @Test
        @OpenFeature({
                @Flag(name = FLAG, value = FLAG_VALUE_STRING, valueType = Integer.class)
        })
        void existingFlagIsRetrieved() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getIntegerValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
        }

        @Test
        @OpenFeature(
                @Flag(name = FLAG, value = "truesadf")
        )
        void strangeFlagValue() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getIntegerValue(FLAG, FALLBACK)).isEqualTo(FALLBACK);
        }

        @Test
        @OpenFeature(
                @Flag(name = FLAG, value = FLAG_VALUE_STRING, valueType = Integer.class)
        )
        void nonExistingFlagIsFallbacked() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getIntegerValue("nonSetFlag", FALLBACK)).isEqualTo(FALLBACK);
        }

        @Test
        @OpenFeature({
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

        @Nested
        @OpenFeature({
                @Flag(name = FLAG, value = FLAG_VALUE_STRING, valueType = Integer.class),
                @Flag(name = FLAG + "2", value = FLAG_VALUE_STRING_ALTERNATIVE, valueType = Integer.class),
        })
        class MultipleFlags {
            @Test
            @OpenFeature({
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
                    domain = SPECIFIC_DOMAIN,
                    value = {
                            @Flag(name = FLAG + "2", value = FLAG_VALUE_STRING, valueType = Integer.class),
                            @Flag(name = FLAG + "3", value = FLAG_VALUE_STRING, valueType = Integer.class),
                    })
            void multipleFlagsOnMultipleDomains() {
                Client client = OpenFeatureAPI.getInstance().getClient();
                assertThat(client.getIntegerValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
                assertThat(client.getIntegerValue(FLAG + "2", FALLBACK)).isEqualTo(FLAG_VALUE_ALTERNATIVE);
                assertThat(client.getIntegerValue(FLAG + "3", FALLBACK)).isEqualTo(FALLBACK);

                Client testSpecific = OpenFeatureAPI.getInstance().getClient(SPECIFIC_DOMAIN);
                assertThat(testSpecific.getIntegerValue(FLAG, FALLBACK)).isEqualTo(FALLBACK);
                assertThat(testSpecific.getIntegerValue(FLAG + "2", FALLBACK)).isEqualTo(FLAG_VALUE);
                assertThat(testSpecific.getIntegerValue(FLAG + "3", FALLBACK)).isEqualTo(FLAG_VALUE);
            }
        }
    }
}
