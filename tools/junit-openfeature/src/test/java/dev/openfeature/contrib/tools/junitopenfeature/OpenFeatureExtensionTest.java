package dev.openfeature.contrib.tools.junitopenfeature;

import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.NoOpProvider;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.providers.memory.InMemoryProvider;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.assertj.core.api.Assertions.assertThat;

@Execution(ExecutionMode.SAME_THREAD)
class OpenFeatureExtensionTest {

    OpenFeatureAPI api = OpenFeatureAPI.getInstance();

    @Nested
    class Initialization {

        @Nested
        class OnMethod {
            @Test
            @OpenFeature({})
            void clientIsSet() {
                assertThat(api).isNotNull();
                assertThat(api.getProvider()).isInstanceOf(InMemoryProvider.class);
            }

            @OpenFeature({})
            @OpenFeature(domain = "test", value = {})
            void clientIsSetMultipleTimes() {
                assertThat(api).isNotNull();
                assertThat(api.getProvider()).isInstanceOf(InMemoryProvider.class);
                assertThat(api.getProvider("test")).isInstanceOf(InMemoryProvider.class);
                Client client = OpenFeatureAPI.getInstance().getClient();
                assertThat(client).isNotNull();
                Client clientTest = OpenFeatureAPI.getInstance().getClient("test");
                assertThat(clientTest).isNotNull();
            }

            @Test
            @OpenFeature(domain = "domain", value = {})
            void clientIsSetWithDomain() {
                assertThat(api).isNotNull();
                assertThat(api.getProvider("domain")).isInstanceOf(InMemoryProvider.class);
                assertThat(api.getProvider()).isInstanceOf(NoOpProvider.class);
                Client client = OpenFeatureAPI.getInstance().getClient("domain");
                assertThat(client).isNotNull();
            }
        }

        @Nested
        @OpenFeature({})
        class OnClass {
            @Test
            void clientIsSet() {
                assertThat(api).isNotNull();
                assertThat(api.getProvider()).isInstanceOf(InMemoryProvider.class);
                Client client = OpenFeatureAPI.getInstance().getClient();
                assertThat(client).isNotNull();
            }
        }

        @Nested
        @OpenFeature(domain = "domain", value = {})
        class OnClassWithDomain {
            @Test
            void clientIsSetWithDomain() {
                assertThat(api).isNotNull();
                assertThat(api.getProvider("domain")).isInstanceOf(InMemoryProvider.class);
                assertThat(api.getProvider()).isInstanceOf(NoOpProvider.class);
                Client client = OpenFeatureAPI.getInstance().getClient("domain");
                assertThat(client).isNotNull();
            }
        }

    }

    private static final String BOOLEAN_FLAG = "boolean-flag";

    @Nested
    class Boolean {
        @Nested
        class SimpleConfig {

            @Nested
            @Flag(name = BOOLEAN_FLAG, value = "true")
            @Flag(name = BOOLEAN_FLAG + "2", value = "true")
            @Flag(name = BOOLEAN_FLAG + "3", value = "true")
            class onClass {
                @Test
                void multipleFlagsSimple() {
                    Client client = OpenFeatureAPI.getInstance().getClient();
                    assertThat(client.getBooleanValue(BOOLEAN_FLAG, false)).isTrue();
                    assertThat(client.getBooleanValue(BOOLEAN_FLAG + "2", false)).isTrue();
                    assertThat(client.getBooleanValue(BOOLEAN_FLAG + "3", false)).isTrue();
                }
            }
            @Test
            @Flag(name = BOOLEAN_FLAG, value = "true")
            void existingSimpleFlagIsRetrieved() {
                Client client = OpenFeatureAPI.getInstance().getClient();
                assertThat(client.getBooleanValue(BOOLEAN_FLAG, false)).isTrue();
            }

            @Test
            @Flag(name = BOOLEAN_FLAG, value = "true")
            @Flag(name = BOOLEAN_FLAG + "2", value = "true")
            @Flag(name = BOOLEAN_FLAG + "3", value = "true")
            void multipleFlagsSimple() {
                Client client = OpenFeatureAPI.getInstance().getClient();
                assertThat(client.getBooleanValue(BOOLEAN_FLAG, false)).isTrue();
                assertThat(client.getBooleanValue(BOOLEAN_FLAG + "2", false)).isTrue();
                assertThat(client.getBooleanValue(BOOLEAN_FLAG + "3", false)).isTrue();
            }
        }

        @Nested
        @OpenFeatureDefaultDomain("testSpecific")
        class SimpleConfigWithDefault {
            @Nested
            @Flag(name = BOOLEAN_FLAG, value = "true")
            @Flag(name = BOOLEAN_FLAG + "2", value = "true")
            @Flag(name = BOOLEAN_FLAG + "3", value = "true")
            class onClass {
                @Test
                void multipleFlagsSimple() {
                    Client client = OpenFeatureAPI.getInstance().getClient("testSpecific");
                    assertThat(client.getBooleanValue(BOOLEAN_FLAG, false)).isTrue();
                    assertThat(client.getBooleanValue(BOOLEAN_FLAG + "2", false)).isTrue();
                    assertThat(client.getBooleanValue(BOOLEAN_FLAG + "3", false)).isTrue();
                }
            }
            @Test
            @Flag(name = BOOLEAN_FLAG, value = "true")
            void existingSimpleFlagIsRetrieved() {
                Client client = OpenFeatureAPI.getInstance().getClient("testSpecific");
                assertThat(client.getBooleanValue(BOOLEAN_FLAG, false)).isTrue();
            }

            @Test
            @Flag(name = BOOLEAN_FLAG, value = "true")
            @Flag(name = BOOLEAN_FLAG + "2", value = "true")
            @Flag(name = BOOLEAN_FLAG + "3", value = "true")
            void multipleFlagsSimple() {
                Client client = OpenFeatureAPI.getInstance().getClient("testSpecific");
                assertThat(client.getBooleanValue(BOOLEAN_FLAG, false)).isTrue();
                assertThat(client.getBooleanValue(BOOLEAN_FLAG + "2", false)).isTrue();
                assertThat(client.getBooleanValue(BOOLEAN_FLAG + "3", false)).isTrue();
            }
        }

        @Nested
        class ExtendedConfig {
            @Test
            @OpenFeature({
                    @Flag(name = BOOLEAN_FLAG, value = "true")
            })
            void existingFlagIsRetrieved() {
                Client client = OpenFeatureAPI.getInstance().getClient();
                assertThat(client.getBooleanValue(BOOLEAN_FLAG, false)).isTrue();
            }

            @Test
            @OpenFeature(
                    @Flag(name = BOOLEAN_FLAG, value = "truesadf")
            )
            void strangeFlagValue() {
                Client client = OpenFeatureAPI.getInstance().getClient();
                assertThat(client.getBooleanValue(BOOLEAN_FLAG, false)).isFalse();
            }

            @Test
            @OpenFeature(
                    @Flag(name = BOOLEAN_FLAG, value = "true")
            )
            void nonExistingFlagIsFallbacked() {
                Client client = OpenFeatureAPI.getInstance().getClient();
                assertThat(client.getBooleanValue("nonSetFlag", false)).isFalse();
            }

            @Test
            @OpenFeature({
                    @Flag(name = BOOLEAN_FLAG, value = "true"),
                    @Flag(name = BOOLEAN_FLAG + "2", value = "true"),
                    @Flag(name = BOOLEAN_FLAG + "3", value = "true"),
            })
            void multipleFlags() {
                Client client = OpenFeatureAPI.getInstance().getClient();
                assertThat(client.getBooleanValue(BOOLEAN_FLAG, false)).isTrue();
                assertThat(client.getBooleanValue(BOOLEAN_FLAG + "2", false)).isTrue();
                assertThat(client.getBooleanValue(BOOLEAN_FLAG + "3", false)).isTrue();
            }

            @Nested
            @OpenFeature({
                    @Flag(name = BOOLEAN_FLAG, value = "true"),
                    @Flag(name = BOOLEAN_FLAG + "2", value = "false"),
            })
            class MultipleFlags {
                @Test
                @OpenFeature({
                        @Flag(name = BOOLEAN_FLAG + "2", value = "true"),
                        @Flag(name = BOOLEAN_FLAG + "3", value = "true"),
                })
                void multipleFlags() {
                    Client client = OpenFeatureAPI.getInstance().getClient();
                    assertThat(client.getBooleanValue(BOOLEAN_FLAG, false)).isTrue();
                    assertThat(client.getBooleanValue(BOOLEAN_FLAG + "2", false)).isTrue();
                    assertThat(client.getBooleanValue(BOOLEAN_FLAG + "3", false)).isTrue();
                }

                @Test
                @OpenFeature(
                        domain = "testSpecific",
                        value = {
                                @Flag(name = BOOLEAN_FLAG + "2", value = "true"),
                                @Flag(name = BOOLEAN_FLAG + "3", value = "true"),
                        })
                void multipleFlagsOnMultipleDomains() {
                    Client client = OpenFeatureAPI.getInstance().getClient();
                    assertThat(client.getBooleanValue(BOOLEAN_FLAG, false)).isTrue();
                    assertThat(client.getBooleanValue(BOOLEAN_FLAG + "2", true)).isFalse();
                    assertThat(client.getBooleanValue(BOOLEAN_FLAG + "3", false)).isFalse();

                    Client testSpecific = OpenFeatureAPI.getInstance().getClient("testSpecific");
                    assertThat(testSpecific.getBooleanValue(BOOLEAN_FLAG, false)).isFalse();
                    assertThat(testSpecific.getBooleanValue(BOOLEAN_FLAG + "2", false)).isTrue();
                    assertThat(testSpecific.getBooleanValue(BOOLEAN_FLAG + "3", false)).isTrue();
                }
            }
        }
    }
}
