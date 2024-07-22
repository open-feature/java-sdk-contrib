package dev.openfeature.contrib.tools.junitopenfeature;

import dev.openfeature.sdk.NoOpProvider;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.providers.memory.Flag;
import dev.openfeature.sdk.providers.memory.InMemoryProvider;
import org.apache.commons.lang3.BooleanUtils;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junitpioneer.internal.PioneerAnnotationUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * JUnit5 Extension for OpenFeature.
 */
public class OpenFeatureExtension implements BeforeEachCallback, AfterEachCallback {

    OpenFeatureAPI api = OpenFeatureAPI.getInstance();

    private static Map<String, Map<String, Flag<?>>> handleExtendedConfiguration(
            ExtensionContext extensionContext,
            Map<String, Map<String, Flag<?>>> configuration
    ) {
        PioneerAnnotationUtils
                .findAllEnclosingRepeatableAnnotations(extensionContext, OpenFeature.class)
                .forEachOrdered(annotation -> {
                    Map<String, Flag<?>> domainFlags = configuration.getOrDefault(annotation.domain(), new HashMap<>());

                    Arrays.stream(annotation.value())
                            .filter(flag -> !domainFlags.containsKey(flag.name()))
                            .forEach(flag -> {
                                Flag.FlagBuilder<?> builder = generateFlagBuilder(flag);
                                domainFlags.put(flag.name(), builder.build());
                            });
                    configuration.put(annotation.domain(), domainFlags);
                });
        return configuration;
    }

    private static Map<String, Map<String, Flag<?>>> handleSimpleConfiguration(ExtensionContext extensionContext) {
        Map<String, Map<String, Flag<?>>> configuration = new HashMap<>();
        String defaultDomain = PioneerAnnotationUtils
                .findClosestEnclosingAnnotation(extensionContext, OpenFeatureDefaultDomain.class)
                .map(OpenFeatureDefaultDomain::value).orElse("");
        PioneerAnnotationUtils
                .findAllEnclosingRepeatableAnnotations(
                        extensionContext,
                        dev.openfeature.contrib.tools.junitopenfeature.Flag.class)
                .forEachOrdered(flag -> {
                    Map<String, Flag<?>> domainFlags = configuration.getOrDefault(defaultDomain, new HashMap<>());
                    if (!domainFlags.containsKey(flag.name())) {
                        Flag.FlagBuilder<?> builder = generateFlagBuilder(flag);
                        domainFlags.put(flag.name(), builder.build());
                        configuration.put(defaultDomain, domainFlags);
                    }
                });

        return configuration;
    }

    private static Flag.FlagBuilder<?> generateFlagBuilder(dev.openfeature.contrib.tools.junitopenfeature.Flag flag) {
        Flag.FlagBuilder<?> builder;
        switch (flag.valueType().getSimpleName()) {
            case "Boolean":
                builder = Flag.<Boolean>builder();
                builder.variant(flag.value(), BooleanUtils.toBoolean(flag.value()));
                break;
            case "String":
                builder = Flag.<String>builder();
                builder.variant(flag.value(), flag.value());
                break;
            case "Integer":
                builder = Flag.<Integer>builder();
                builder.variant(flag.value(), Integer.parseInt(flag.value()));
                break;
            case "Double":
                builder = Flag.<Double>builder();
                builder.variant(flag.value(), Double.parseDouble(flag.value()));
                break;
            default:
                throw new IllegalArgumentException("Unsupported flag type: " + flag.value());
        }
        builder.defaultVariant(flag.value());
        return builder;
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) throws Exception {

        @SuppressWarnings("unchecked") Map<String, Map<String, Flag<?>>> configuration =
                (Map<String, Map<String, Flag<?>>>) getStore(extensionContext).get("config");
        for (Map.Entry<String, Map<String, Flag<?>>> stringMapEntry : configuration.entrySet()) {
            if (stringMapEntry.getKey().isEmpty()) {
                api.setProvider(new NoOpProvider());
            } else {
                api.setProvider(stringMapEntry.getKey(), new NoOpProvider());
            }
        }
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        Map<String, Map<String, Flag<?>>> configuration = handleSimpleConfiguration(extensionContext);
        configuration.putAll(handleExtendedConfiguration(extensionContext, configuration));

        for (Map.Entry<String, Map<String, Flag<?>>> stringMapEntry : configuration.entrySet()) {
            InMemoryProvider inMemoryProvider = new InMemoryProvider(stringMapEntry.getValue());
            if (stringMapEntry.getKey().isEmpty()) {
                api.setProvider(inMemoryProvider);
            } else {
                api.setProvider(stringMapEntry.getKey(), inMemoryProvider);
            }
        }

        getStore(extensionContext).put("config", configuration);
    }

    private ExtensionContext.Store getStore(ExtensionContext context) {
        return context.getStore(ExtensionContext.Namespace.create(getClass()));
    }
}
