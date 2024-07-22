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
}
