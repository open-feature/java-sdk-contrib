package dev.openfeature.contrib.providers.flagd.e2e.steps;

import dev.openfeature.contrib.providers.flagd.Config;
import dev.openfeature.contrib.providers.flagd.resolver.rpc.cache.CacheType;
import dev.openfeature.sdk.Value;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

public final class Utils {

    private Utils() {}

    public static Object convert(String value, String type) throws ClassNotFoundException, IOException {
        if (Objects.equals(value, "null")) return null;
        switch (type) {
            case "Boolean":
                return Boolean.parseBoolean(value);
            case "String":
                return value;
            case "Integer":
                return Integer.parseInt(value);
            case "Float":
                return Double.parseDouble(value);
            case "Long":
                return Long.parseLong(value);
            case "ResolverType":
                switch (value.toLowerCase()) {
                    case "in-process":
                        return Config.Resolver.IN_PROCESS;
                    case "rpc":
                        return Config.Resolver.RPC;
                    case "file":
                        return Config.Resolver.FILE;
                    default:
                        throw new RuntimeException("Unknown resolver type: " + value);
                }
            case "CacheType":
                return CacheType.valueOf(value.toUpperCase()).getValue();
            case "StringList":
                return List.of(value);
            case "Object":
                return Value.objectToValue(new ObjectMapper().readValue(value, Object.class));
        }
        throw new RuntimeException("Unknown config type: " + type);
    }
}
