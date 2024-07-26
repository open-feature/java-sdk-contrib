package dev.openfeature.contrib.tools.junitopenfeature;

import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.OpenFeatureAPI;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
        class onClass {
            @Test
            void multipleFlagsSimple() {
                Client client = OpenFeatureAPI.getInstance().getClient();
                assertThat(client.getStringValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
                assertThat(client.getStringValue(FLAG + "2", FALLBACK)).isEqualTo(FLAG_VALUE);
                assertThat(client.getStringValue(FLAG + "3", FALLBACK)).isEqualTo(FLAG_VALUE);
            }
        }

        @Test
        @Flag(name = FLAG, value = FLAG_VALUE, valueType = String.class)
        void existingSimpleFlagIsRetrieved() {
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
    }

    @Nested
    @OpenFeatureDefaultDomain(SPECIFIC_DOMAIN)
    class SimpleConfigWithDefault {
        @Nested
        @Flag(name = FLAG , value = FLAG_VALUE, valueType = String.class)
        @Flag(name = FLAG + "2" , value = FLAG_VALUE, valueType = String.class)
        @Flag(name = FLAG + "3" , value = FLAG_VALUE, valueType = String.class)
        class onClass {
            @Test
            void multipleFlagsSimple() {
                Client client = OpenFeatureAPI.getInstance().getClient("testSpecific");
                assertThat(client.getStringValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
                assertThat(client.getStringValue(FLAG + "2", FALLBACK)).isEqualTo(FLAG_VALUE);
                assertThat(client.getStringValue(FLAG + "3", FALLBACK)).isEqualTo(FLAG_VALUE);
            }
        }

        @Test
        @Flag(name = FLAG , value = FLAG_VALUE, valueType = String.class)
        void existingSimpleFlagIsRetrieved() {
            Client client = OpenFeatureAPI.getInstance().getClient(SPECIFIC_DOMAIN);
            assertThat(client.getStringValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
        }

        @Test
        @Flag(name = FLAG , value = FLAG_VALUE, valueType = String.class)
        @Flag(name = FLAG + "2" , value = FLAG_VALUE, valueType = String.class)
        @Flag(name = FLAG + "3" , value = FLAG_VALUE, valueType = String.class)
        void multipleFlagsSimple() {
            Client client = OpenFeatureAPI.getInstance().getClient(SPECIFIC_DOMAIN);
            assertThat(client.getStringValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
            assertThat(client.getStringValue(FLAG + "2", FALLBACK)).isEqualTo(FLAG_VALUE);
            assertThat(client.getStringValue(FLAG + "3", FALLBACK)).isEqualTo(FLAG_VALUE);
        }
    }

    @Nested
    class ExtendedConfig {
        @Test
        @OpenFeature({
                @Flag(name = FLAG , value = FLAG_VALUE, valueType = String.class)
        })
        void existingFlagIsRetrieved() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getStringValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
        }

        @Test
        @OpenFeature(
                @Flag(name = FLAG, value = "truesadf")
        )
        void strangeFlagValue() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getStringValue(FLAG, FALLBACK)).isEqualTo(FALLBACK);
        }

        @Test
        @OpenFeature(
                @Flag(name = FLAG , value = FLAG_VALUE, valueType = String.class)
        )
        void nonExistingFlagIsFallbacked() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getStringValue("nonSetFlag", FALLBACK)).isEqualTo(FALLBACK);
        }

        @Test
        @OpenFeature({
                @Flag(name = FLAG , value = FLAG_VALUE, valueType = String.class),
                @Flag(name = FLAG + "2" , value = FLAG_VALUE, valueType = String.class),
                @Flag(name = FLAG + "3" , value = FLAG_VALUE, valueType = String.class),
        })
        void multipleFlags() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getStringValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
            assertThat(client.getStringValue(FLAG + "2", FALLBACK)).isEqualTo(FLAG_VALUE);
            assertThat(client.getStringValue(FLAG + "3", FALLBACK)).isEqualTo(FLAG_VALUE);
        }

        @Nested
        @OpenFeature({
                @Flag(name = FLAG , value = FLAG_VALUE, valueType = String.class),
                @Flag(name = FLAG + "2", value = FLAG_VALUE_ALTERNATIVE, valueType = String.class),
        })
        class MultipleFlags {
            @Test
            @OpenFeature({
                    @Flag(name = FLAG + "2" , value = FLAG_VALUE, valueType = String.class),
                    @Flag(name = FLAG + "3" , value = FLAG_VALUE, valueType = String.class),
            })
            void multipleFlags() {
                Client client = OpenFeatureAPI.getInstance().getClient();
                assertThat(client.getStringValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
                assertThat(client.getStringValue(FLAG + "2", FALLBACK)).isEqualTo(FLAG_VALUE);
                assertThat(client.getStringValue(FLAG + "3", FALLBACK)).isEqualTo(FLAG_VALUE);
            }

            @Test
            @OpenFeature(
                    domain = SPECIFIC_DOMAIN,
                    value = {
                            @Flag(name = FLAG + "2" , value = FLAG_VALUE, valueType = String.class),
                            @Flag(name = FLAG + "3" , value = FLAG_VALUE, valueType = String.class),
                    })
            void multipleFlagsOnMultipleDomains() {
                Client client = OpenFeatureAPI.getInstance().getClient();
                assertThat(client.getStringValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
                assertThat(client.getStringValue(FLAG + "2", FALLBACK)).isEqualTo(FLAG_VALUE_ALTERNATIVE);
                assertThat(client.getStringValue(FLAG + "3", FALLBACK)).isEqualTo(FALLBACK);

                Client testSpecific = OpenFeatureAPI.getInstance().getClient(SPECIFIC_DOMAIN);
                assertThat(testSpecific.getStringValue(FLAG, FALLBACK)).isEqualTo(FALLBACK);
                assertThat(testSpecific.getStringValue(FLAG + "2", FALLBACK)).isEqualTo(FLAG_VALUE);
                assertThat(testSpecific.getStringValue(FLAG + "3", FALLBACK)).isEqualTo(FLAG_VALUE);
            }
        }
    }
}
