package dev.openfeature.contrib.providers.gofeatureflag.evaluator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openfeature.contrib.providers.gofeatureflag.GoFeatureFlagProviderOptions;
import dev.openfeature.contrib.providers.gofeatureflag.util.Const;
import dev.openfeature.contrib.providers.gofeatureflag.util.GoffApiMock;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.Reason;
import dev.openfeature.sdk.Value;
import java.io.IOException;
import lombok.SneakyThrows;
import lombok.val;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RemoteEvaluatorTest {
    private MockWebServer server;

    @BeforeEach
    void beforeEach() throws IOException {
        this.server = new MockWebServer();
        this.server.setDispatcher(new GoffApiMock(GoffApiMock.MockMode.DEFAULT).dispatcher);
        this.server.start();
    }

    @AfterEach
    void afterEach() throws IOException {
        this.server.close();
        this.server = null;
    }

    private RemoteEvaluator evaluator(String endpoint, String apiKey) {
        return new RemoteEvaluator(GoFeatureFlagProviderOptions.builder()
                .endpoint(endpoint)
                .apiKey(apiKey)
                .timeout(1000)
                .build());
    }

    private RemoteEvaluator evaluator() {
        return evaluator(this.server.url("").toString(), null);
    }

    @DisplayName("should resolve a boolean flag")
    @Test
    void shouldResolveABooleanFlag() {
        val got = evaluator().getBooleanEvaluation("bool_flag", false, new ImmutableContext("user-key"));

        assertEquals(true, got.getValue());
        assertEquals("enabled", got.getVariant());
        assertEquals(Reason.TARGETING_MATCH.name(), got.getReason());
        assertNull(got.getErrorCode());
    }

    @DisplayName("should resolve a string flag")
    @Test
    void shouldResolveAStringFlag() {
        val got = evaluator().getStringEvaluation("string_flag", "default", new ImmutableContext("user-key"));

        assertEquals("string value", got.getValue());
        assertEquals("variantA", got.getVariant());
        assertNull(got.getErrorCode());
    }

    @DisplayName("should resolve an integer flag")
    @Test
    void shouldResolveAnIntegerFlag() {
        val got = evaluator().getIntegerEvaluation("int_flag", 0, new ImmutableContext("user-key"));

        assertEquals(100, got.getValue());
        assertEquals("variantA", got.getVariant());
        assertNull(got.getErrorCode());
    }

    @DisplayName("should resolve a double flag")
    @Test
    void shouldResolveADoubleFlag() {
        val got = evaluator().getDoubleEvaluation("double_flag", 0.0, new ImmutableContext("user-key"));

        assertEquals(100.11, got.getValue());
        assertEquals("variantA", got.getVariant());
        assertNull(got.getErrorCode());
    }

    @DisplayName("should resolve an object flag")
    @Test
    void shouldResolveAnObjectFlag() {
        val got = evaluator().getObjectEvaluation("object_flag", new Value(), new ImmutableContext("user-key"));

        assertEquals("foo", got.getValue().asStructure().getValue("name").asString());
        assertEquals("variantA", got.getVariant());
        assertNull(got.getErrorCode());
    }

    @DisplayName("should pass the relay proxy metadata through untouched")
    @Test
    void shouldPassTheRelayProxyMetadataThrough() {
        val got = evaluator().getBooleanEvaluation("bool_flag", false, new ImmutableContext("user-key"));

        // gofeatureflag_cacheable belongs to the relay proxy, the provider must not strip it
        assertEquals(true, got.getFlagMetadata().getBoolean("gofeatureflag_cacheable"));
        assertEquals("A flag that is always off", got.getFlagMetadata().getString("description"));
    }

    @DisplayName("should pass gofeatureflag_version through untouched")
    @Test
    void shouldPassTheRelayProxyVersionThrough() {
        val got = evaluator().getBooleanEvaluation("metadata_with_version", false, new ImmutableContext("user-key"));

        assertEquals(true, got.getFlagMetadata().getBoolean("gofeatureflag_cacheable"));
        assertEquals("1.2.3", got.getFlagMetadata().getString("gofeatureflag_version"));
        assertEquals(
                "A flag carrying both relay proxy metadata keys",
                got.getFlagMetadata().getString("description"));
    }

    @DisplayName("should evaluate a flag whose metadata carries no relay proxy key")
    @Test
    void shouldEvaluateAFlagWithoutRelayProxyMetadataKeys() {
        val got =
                evaluator().getBooleanEvaluation("metadata_without_goff_keys", false, new ImmutableContext("user-key"));

        assertEquals(true, got.getValue());
        assertNull(got.getErrorCode());
        assertNull(got.getFlagMetadata().getBoolean("gofeatureflag_cacheable"));
        assertEquals(
                "A relay proxy that adds no gofeatureflag_ keys",
                got.getFlagMetadata().getString("description"));
    }

    @DisplayName("should evaluate a flag whose response carries no metadata at all")
    @Test
    void shouldEvaluateAFlagWithoutAnyMetadata() {
        val got = evaluator().getBooleanEvaluation("metadata_absent", false, new ImmutableContext("user-key"));

        assertEquals(true, got.getValue());
        assertNull(got.getErrorCode());
    }

    @DisplayName("should not claim a remote evaluation in the flag metadata")
    @Test
    void shouldNotClaimARemoteEvaluationInTheFlagMetadata() {
        val got = evaluator().getBooleanEvaluation("bool_flag", false, new ImmutableContext("user-key"));

        // gofeatureflag_evaluated_remotely marks a result recovered by the remote fallback of the
        // in-process evaluator, so a plain remote evaluation must not carry it
        assertNull(got.getFlagMetadata().getBoolean("gofeatureflag_evaluated_remotely"));
    }

    @SneakyThrows
    @DisplayName("should send the API key as an X-API-Key header")
    @Test
    void shouldSendTheApiKeyAsAnApiKeyHeader() {
        evaluator(this.server.url("").toString(), "my-api-key")
                .getBooleanEvaluation("bool_flag", false, new ImmutableContext("user-key"));

        val request = this.server.takeRequest();
        assertEquals("my-api-key", request.getHeader(Const.HTTP_HEADER_API_KEY));
    }

    @SneakyThrows
    @DisplayName("should not send an API key header when none is configured")
    @Test
    void shouldNotSendAnApiKeyHeaderWhenNoneIsConfigured() {
        evaluator().getBooleanEvaluation("bool_flag", false, new ImmutableContext("user-key"));

        val request = this.server.takeRequest();
        assertNull(request.getHeader(Const.HTTP_HEADER_API_KEY));
    }

    @SneakyThrows
    @DisplayName("should keep the path prefix of the endpoint")
    @Test
    void shouldKeepThePathPrefixOfTheEndpoint() {
        evaluator(this.server.url("/gofeatureflagproxy/").toString(), null)
                .getBooleanEvaluation("bool_flag", false, new ImmutableContext("user-key"));

        val request = this.server.takeRequest();
        assertEquals("/gofeatureflagproxy/ofrep/v1/evaluate/flags/bool_flag", request.getPath());
    }

    @DisplayName("should report FLAG_NOT_FOUND for an unknown flag")
    @Test
    void shouldReportFlagNotFoundForAnUnknownFlag() {
        val got = evaluator().getBooleanEvaluation("DOES_NOT_EXIST", false, new ImmutableContext("user-key"));

        // the OFREP provider answers with an error code rather than raising, unlike the in-process one
        assertEquals(ErrorCode.FLAG_NOT_FOUND, got.getErrorCode());
        assertEquals(false, got.getValue());
    }

    @DisplayName("should report every flag as trackable")
    @Test
    void shouldReportEveryFlagAsTrackable() {
        // in remote mode the relay proxy collects the evaluations itself, so the provider cannot
        // know what is tracked and must not filter anything out
        assertTrue(evaluator().isFlagTrackable("bool_flag"));
        assertTrue(evaluator().isFlagTrackable("DOES_NOT_EXIST"));
    }
}
