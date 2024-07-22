package dev.openfeature.contrib.tools.junitopenfeature;

import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.OpenFeatureAPI;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.assertj.core.api.Assertions.assertThat;

@Execution(ExecutionMode.SAME_THREAD)
class BooleanFlagTest {

    private static final String FLAG = "boolean-flag";

    @Nested
    class SimpleConfig {

        @Nested
        @Flag(name = FLAG, value = "true")
        @Flag(name = FLAG + "2", value = "true")
        @Flag(name = FLAG + "3", value = "true")
        class onClass {
            @Test
            void multipleFlagsSimple() {
                Client client = OpenFeatureAPI.getInstance().getClient();
                assertThat(client.getBooleanValue(FLAG, false)).isTrue();
                assertThat(client.getBooleanValue(FLAG + "2", false)).isTrue();
                assertThat(client.getBooleanValue(FLAG + "3", false)).isTrue();
            }
        }

        @Test
        @Flag(name = FLAG, value = "true")
        void existingSimpleFlagIsRetrieved() {
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
    }

    @Nested
    @OpenFeatureDefaultDomain("testSpecific")
    class SimpleConfigWithDefault {
        @Nested
        @Flag(name = FLAG, value = "true")
        @Flag(name = FLAG + "2", value = "true")
        @Flag(name = FLAG + "3", value = "true")
        class onClass {
            @Test
            void multipleFlagsSimple() {
                Client client = OpenFeatureAPI.getInstance().getClient("testSpecific");
                assertThat(client.getBooleanValue(FLAG, false)).isTrue();
                assertThat(client.getBooleanValue(FLAG + "2", false)).isTrue();
                assertThat(client.getBooleanValue(FLAG + "3", false)).isTrue();
            }
        }

        @Test
        @Flag(name = FLAG, value = "true")
        void existingSimpleFlagIsRetrieved() {
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
    }

    @Nested
    class ExtendedConfig {
        @Test
        @OpenFeature({
                @Flag(name = FLAG, value = "true")
        })
        void existingFlagIsRetrieved() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getBooleanValue(FLAG, false)).isTrue();
        }

        @Test
        @OpenFeature(
                @Flag(name = FLAG, value = "truesadf")
        )
        void strangeFlagValue() {
            Client client = OpenFeatureAPI.getInstance().getClient();
            assertThat(client.getBooleanValue(FLAG, false)).isFalse();
        }

        @Test
        @OpenFeature(
                @Flag(name = FLAG, value = "true")
        )
        void nonExistingFlagIsFallbacked() {
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

        @Nested
        @OpenFeature({
                @Flag(name = FLAG, value = "true"),
                @Flag(name = FLAG + "2", value = "false"),
        })
        class MultipleFlags {
            @Test
            @OpenFeature({
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
                    domain = "testSpecific",
                    value = {
                            @Flag(name = FLAG + "2", value = "true"),
                            @Flag(name = FLAG + "3", value = "true"),
                    })
            void multipleFlagsOnMultipleDomains() {
                Client client = OpenFeatureAPI.getInstance().getClient();
                assertThat(client.getBooleanValue(FLAG, false)).isTrue();
                assertThat(client.getBooleanValue(FLAG + "2", true)).isFalse();
                assertThat(client.getBooleanValue(FLAG + "3", false)).isFalse();

                Client testSpecific = OpenFeatureAPI.getInstance().getClient("testSpecific");
                assertThat(testSpecific.getBooleanValue(FLAG, false)).isFalse();
                assertThat(testSpecific.getBooleanValue(FLAG + "2", false)).isTrue();
                assertThat(testSpecific.getBooleanValue(FLAG + "3", false)).isTrue();
            }
        }
    }
}
