package dev.openfeature.contrib.tools.junitopenfeature;

import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.OpenFeatureAPI;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
                assertThat(api.getProvider()).isInstanceOf(TestProvider.class);
            }

            @OpenFeature({})
            @OpenFeature(domain = "test", value = {})
            void clientIsSetMultipleTimes() {
                assertThat(api).isNotNull();
                assertThat(api.getProvider()).isInstanceOf(TestProvider.class);
                assertThat(api.getProvider("test")).isInstanceOf(TestProvider.class);
                Client client = OpenFeatureAPI.getInstance().getClient();
                assertThat(client).isNotNull();
                Client clientTest = OpenFeatureAPI.getInstance().getClient("test");
                assertThat(clientTest).isNotNull();
            }

            @Test
            @OpenFeature(domain = "domain", value = {})
            void clientIsSetWithDomain() {
                assertThat(api).isNotNull();
                assertThat(api.getProvider("domain")).isInstanceOf(TestProvider.class);
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
                assertThat(api.getProvider()).isInstanceOf(TestProvider.class);
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
                assertThat(api.getProvider("domain")).isInstanceOf(TestProvider.class);
                Client client = OpenFeatureAPI.getInstance().getClient("domain");
                assertThat(client).isNotNull();
            }
        }
    }
}
