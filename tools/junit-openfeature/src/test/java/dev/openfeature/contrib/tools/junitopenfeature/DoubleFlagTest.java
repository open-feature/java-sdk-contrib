package dev.openfeature.contrib.tools.junitopenfeature;

import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.OpenFeatureAPI;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DoubleFlagTest {

    private static final String FLAG = "double-flag";
    private static final Double FALLBACK = -1.d;
    private static final Double FLAG_VALUE = 1.d;
    private static final String FLAG_VALUE_STRING = "1";
    private static final Double FLAG_VALUE_ALTERNATIVE = 0.d;
    private static final String FLAG_VALUE_STRING_ALTERNATIVE = "0";
    private static final String SPECIFIC_DOMAIN = "testSpecific";


    @Nested
    class SimpleConfig {

        @Test
        @Flag(name = FLAG, value = FLAG_VALUE_STRING, valueType = Double.class)
        void existingSimpleFlagIsRetrieved() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getDoubleValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
        }

        @Test
        @Flag(name = FLAG, value = FLAG_VALUE_STRING, valueType = Double.class)
        @Flag(name = FLAG + "2", value = FLAG_VALUE_STRING, valueType = Double.class)
        @Flag(name = FLAG + "3", value = FLAG_VALUE_STRING, valueType = Double.class)
        void multipleFlagsSimple() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getDoubleValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
            assertThat(client.getDoubleValue(FLAG + "2", FALLBACK)).isEqualTo(FLAG_VALUE);
            assertThat(client.getDoubleValue(FLAG + "3", FALLBACK)).isEqualTo(FLAG_VALUE);
        }

        @Nested
        @Flag(name = FLAG, value = FLAG_VALUE_STRING  , valueType = Double.class)
        @Flag(name = FLAG + "2", value = FLAG_VALUE_STRING, valueType = Double.class)
        @Flag(name = FLAG + "3", value = FLAG_VALUE_STRING, valueType = Double.class)
        class onClass {
            @Test
            void multipleFlagsSimple() {
                Client client = OpenFeatureAPI.getInstance().getClient();
                assertThat(client.getDoubleValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
                assertThat(client.getDoubleValue(FLAG + "2", FALLBACK)).isEqualTo(FLAG_VALUE);
                assertThat(client.getDoubleValue(FLAG + "3", FALLBACK)).isEqualTo(FLAG_VALUE);
            }
        }
    }

    @Nested
    @OpenFeatureDefaultDomain(SPECIFIC_DOMAIN)
    class SimpleConfigWithDefault {
        @Test
        @Flag(name = FLAG, value = FLAG_VALUE_STRING, valueType = Double.class)
        void existingSimpleFlagIsRetrieved() {
            Client client = OpenFeatureAPI.getInstance().getClient(SPECIFIC_DOMAIN);
            assertThat(client.getDoubleValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
        }

        @Test
        @Flag(name = FLAG, value = FLAG_VALUE_STRING, valueType = Double.class)
        @Flag(name = FLAG + "2", value = FLAG_VALUE_STRING, valueType = Double.class)
        @Flag(name = FLAG + "3", value = FLAG_VALUE_STRING, valueType = Double.class)
        void multipleFlagsSimple() {
            Client client = OpenFeatureAPI.getInstance().getClient(SPECIFIC_DOMAIN);
            assertThat(client.getDoubleValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
            assertThat(client.getDoubleValue(FLAG + "2", FALLBACK)).isEqualTo(FLAG_VALUE);
            assertThat(client.getDoubleValue(FLAG + "3", FALLBACK)).isEqualTo(FLAG_VALUE);
        }

        @Nested
        @Flag(name = FLAG, value = FLAG_VALUE_STRING, valueType = Double.class)
        @Flag(name = FLAG + "2", value = FLAG_VALUE_STRING, valueType = Double.class)
        @Flag(name = FLAG + "3", value = FLAG_VALUE_STRING, valueType = Double.class)
        class onClass {
            @Test
            void multipleFlagsSimple() {
                Client client = OpenFeatureAPI.getInstance().getClient("testSpecific");
                assertThat(client.getDoubleValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
                assertThat(client.getDoubleValue(FLAG + "2", FALLBACK)).isEqualTo(FLAG_VALUE);
                assertThat(client.getDoubleValue(FLAG + "3", FALLBACK)).isEqualTo(FLAG_VALUE);
            }
        }
    }

    @Nested
    class ExtendedConfig {
        @Test
        @OpenFeature({
                @Flag(name = FLAG, value = FLAG_VALUE_STRING, valueType = Double.class)
        })
        void existingFlagIsRetrieved() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getDoubleValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
        }

        @Test
        @OpenFeature(
                @Flag(name = FLAG, value = "truesadf")
        )
        void strangeFlagValue() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getDoubleValue(FLAG, FALLBACK)).isEqualTo(FALLBACK);
        }

        @Test
        @OpenFeature(
                @Flag(name = FLAG, value = FLAG_VALUE_STRING, valueType = Double.class)
        )
        void nonExistingFlagIsFallbacked() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getDoubleValue("nonSetFlag", FALLBACK)).isEqualTo(FALLBACK);
        }

        @Test
        @OpenFeature({
                @Flag(name = FLAG, value = FLAG_VALUE_STRING, valueType = Double.class),
                @Flag(name = FLAG + "2", value = FLAG_VALUE_STRING, valueType = Double.class),
                @Flag(name = FLAG + "3", value = FLAG_VALUE_STRING, valueType = Double.class),
        })
        void multipleFlags() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getDoubleValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
            assertThat(client.getDoubleValue(FLAG + "2", FALLBACK)).isEqualTo(FLAG_VALUE);
            assertThat(client.getDoubleValue(FLAG + "3", FALLBACK)).isEqualTo(FLAG_VALUE);
        }

        @Nested
        @OpenFeature({
                @Flag(name = FLAG, value = FLAG_VALUE_STRING, valueType = Double.class),
                @Flag(name = FLAG + "2", value = FLAG_VALUE_STRING_ALTERNATIVE, valueType = Double.class),
        })
        class MultipleFlags {
            @Test
            @OpenFeature({
                    @Flag(name = FLAG + "2", value = FLAG_VALUE_STRING, valueType = Double.class),
                    @Flag(name = FLAG + "3", value = FLAG_VALUE_STRING, valueType = Double.class),
            })
            void multipleFlags() {
                Client client = OpenFeatureAPI.getInstance().getClient();
                assertThat(client.getDoubleValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
                assertThat(client.getDoubleValue(FLAG + "2", FALLBACK)).isEqualTo(FLAG_VALUE);
                assertThat(client.getDoubleValue(FLAG + "3", FALLBACK)).isEqualTo(FLAG_VALUE);
            }

            @Test
            @OpenFeature(
                    domain = SPECIFIC_DOMAIN,
                    value = {
                            @Flag(name = FLAG + "2", value = FLAG_VALUE_STRING, valueType = Double.class),
                            @Flag(name = FLAG + "3", value = FLAG_VALUE_STRING, valueType = Double.class),
                    })
            void multipleFlagsOnMultipleDomains() {
                Client client = OpenFeatureAPI.getInstance().getClient();
                assertThat(client.getDoubleValue(FLAG, FALLBACK)).isEqualTo(FLAG_VALUE);
                assertThat(client.getDoubleValue(FLAG + "2", FALLBACK)).isEqualTo(FLAG_VALUE_ALTERNATIVE);
                assertThat(client.getDoubleValue(FLAG + "3", FALLBACK)).isEqualTo(FALLBACK);

                Client testSpecific = OpenFeatureAPI.getInstance().getClient(SPECIFIC_DOMAIN);
                assertThat(testSpecific.getDoubleValue(FLAG, FALLBACK)).isEqualTo(FALLBACK);
                assertThat(testSpecific.getDoubleValue(FLAG + "2", FALLBACK)).isEqualTo(FLAG_VALUE);
                assertThat(testSpecific.getDoubleValue(FLAG + "3", FALLBACK)).isEqualTo(FLAG_VALUE);
            }
        }
    }
}
