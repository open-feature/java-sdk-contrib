package dev.openfeature.contrib.providers.ofrep;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.time.Duration;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junitpioneer.jupiter.SetEnvironmentVariable;

class OfrepEnvironmentTest {

    private static Function<String, String> env(String name, String value) {
        return Map.of(name, value)::get;
    }

    private static final Function<String, String> EMPTY = name -> null;

    @Test
    void usesDefaultsWithoutEnvironmentVariables() {
        assertEquals("http://localhost:8016", OfrepEnvironment.baseUrl(EMPTY));
        assertEquals(Duration.ofSeconds(10), OfrepEnvironment.requestTimeout(EMPTY));
        assertEquals(ImmutableMap.of(), OfrepEnvironment.headers(EMPTY));
    }

    @Test
    void readsEndpoint() {
        assertEquals(
                "http://flags.example.com:2321",
                OfrepEnvironment.baseUrl(env("OFREP_ENDPOINT", " http://flags.example.com:2321 ")));
        assertEquals("http://localhost:8016", OfrepEnvironment.baseUrl(env("OFREP_ENDPOINT", "  ")));
    }

    @Test
    void readsTimeout() {
        assertEquals(Duration.ofMillis(5000), OfrepEnvironment.requestTimeout(env("OFREP_TIMEOUT_MS", "5000")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0", "-5", "1.5", ""})
    void fallsBackToTheDefaultTimeoutOnInvalidValues(String value) {
        assertEquals(Duration.ofSeconds(10), OfrepEnvironment.requestTimeout(env("OFREP_TIMEOUT_MS", value)));
    }

    @Test
    void parsesHeaders() {
        assertEquals(
                ImmutableMap.of(
                        "Authorization", ImmutableList.of("Bearer token"),
                        "X-Custom", ImmutableList.of("a=b"),
                        "X-Plus", ImmutableList.of("1+1")),
                OfrepEnvironment.headers(
                        env("OFREP_HEADERS", "Authorization=Bearer%20token, X-Custom = a=b ,X-Plus=1+1")));
    }

    @Test
    void skipsMalformedHeaderEntries() {
        assertEquals(
                ImmutableMap.of("X-Ok", ImmutableList.of("1"), "X-Empty", ImmutableList.of("")),
                OfrepEnvironment.headers(env("OFREP_HEADERS", "novalue,=nokey,,X-Ok=1,X-Empty=")));
    }

    @Test
    void treatsAnEncodedCommaAsASeparator() {
        assertEquals(
                ImmutableMap.of("A", ImmutableList.of("1"), "B", ImmutableList.of("2")),
                OfrepEnvironment.headers(env("OFREP_HEADERS", "A=1%2CB=2")));
    }

    @Test
    void ignoresInvalidPercentEncoding() {
        assertEquals(ImmutableMap.of(), OfrepEnvironment.headers(env("OFREP_HEADERS", "A=%zz")));
    }

    @Test
    @SetEnvironmentVariable(key = "OFREP_ENDPOINT", value = "http://flags.example.com:2321")
    @SetEnvironmentVariable(key = "OFREP_TIMEOUT_MS", value = "1500")
    @SetEnvironmentVariable(key = "OFREP_HEADERS", value = "Authorization=Bearer%20123")
    void builderUsesEnvironmentVariablesAsDefaults() {
        OfrepProviderOptions options = OfrepProviderOptions.builder().build();

        assertEquals("http://flags.example.com:2321", options.getBaseUrl());
        assertEquals(Duration.ofMillis(1500), options.getRequestTimeout());
        assertEquals(ImmutableMap.of("Authorization", ImmutableList.of("Bearer 123")), options.getHeaders());
    }

    @Test
    @SetEnvironmentVariable(key = "OFREP_ENDPOINT", value = "http://flags.example.com:2321")
    @SetEnvironmentVariable(key = "OFREP_TIMEOUT_MS", value = "1500")
    @SetEnvironmentVariable(key = "OFREP_HEADERS", value = "Authorization=Bearer%20123")
    void builderValuesTakePrecedenceOverEnvironmentVariables() {
        OfrepProviderOptions options = OfrepProviderOptions.builder()
                .baseUrl("http://localhost:9000")
                .requestTimeout(Duration.ofSeconds(3))
                .headers(ImmutableMap.of("X-Api-Key", ImmutableList.of("k")))
                .build();

        assertEquals("http://localhost:9000", options.getBaseUrl());
        assertEquals(Duration.ofSeconds(3), options.getRequestTimeout());
        assertEquals(ImmutableMap.of("X-Api-Key", ImmutableList.of("k")), options.getHeaders());
    }
}
