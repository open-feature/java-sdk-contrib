package dev.openfeature.contrib.providers.ofrep;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;

/**
 * Reads the OFREP provider defaults from the standard OFREP environment variables (protocol ADR 0007).
 * Programmatic configuration on {@link OfrepProviderOptions.Builder} takes precedence over them.
 */
@Slf4j
final class OfrepEnvironment {

    static final String ENDPOINT = "OFREP_ENDPOINT";
    static final String HEADERS = "OFREP_HEADERS";
    static final String TIMEOUT_MS = "OFREP_TIMEOUT_MS";

    static final String DEFAULT_BASE_URL = "http://localhost:8016";
    static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private OfrepEnvironment() {}

    static String baseUrl(Function<String, String> env) {
        String endpoint = env.apply(ENDPOINT);
        if (endpoint == null || endpoint.trim().isEmpty()) {
            return DEFAULT_BASE_URL;
        }
        return endpoint.trim();
    }

    static Duration requestTimeout(Function<String, String> env) {
        String timeout = env.apply(TIMEOUT_MS);
        if (timeout == null || timeout.trim().isEmpty()) {
            return DEFAULT_REQUEST_TIMEOUT;
        }
        long millis;
        try {
            millis = Long.parseLong(timeout.trim());
        } catch (NumberFormatException e) {
            millis = 0;
        }
        if (millis > 0) {
            return Duration.ofMillis(millis);
        }
        log.warn(
                "Invalid {} value '{}': expected a positive number of milliseconds, using the default of {} ms",
                TIMEOUT_MS,
                timeout,
                DEFAULT_REQUEST_TIMEOUT.toMillis());
        return DEFAULT_REQUEST_TIMEOUT;
    }

    static ImmutableMap<String, ImmutableList<String>> headers(Function<String, String> env) {
        String raw = env.apply(HEADERS);
        if (raw == null || raw.trim().isEmpty()) {
            return ImmutableMap.of();
        }
        String decoded;
        try {
            // percent-decoding only: keep '+' as is instead of turning it into a space
            decoded = URLDecoder.decode(raw.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid percent-encoding in {}, ignoring it: {}", HEADERS, e.getMessage());
            return ImmutableMap.of();
        }

        Map<String, ImmutableList<String>> headers = new LinkedHashMap<>();
        for (String entry : decoded.split(",")) {
            if (entry.trim().isEmpty()) {
                continue;
            }
            int equals = entry.indexOf('=');
            if (equals < 0) {
                log.warn("Malformed entry '{}' in {}: expected Key=Value, skipping it", entry, HEADERS);
                continue;
            }
            String key = entry.substring(0, equals).trim();
            String value = entry.substring(equals + 1).trim();
            if (key.isEmpty()) {
                log.warn("Empty header name in entry '{}' in {}, skipping it", entry, HEADERS);
                continue;
            }
            headers.put(key, ImmutableList.of(value));
        }
        return ImmutableMap.copyOf(headers);
    }
}
