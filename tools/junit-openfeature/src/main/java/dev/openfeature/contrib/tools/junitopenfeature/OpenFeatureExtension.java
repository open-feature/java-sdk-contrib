package dev.openfeature.contrib.tools.junitopenfeature;

import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.providers.memory.Flag;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.BooleanUtils;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.junitpioneer.internal.PioneerAnnotationUtils;

/**
 * JUnit5 Extension for OpenFeature.
 */
public class OpenFeatureExtension implements BeforeEachCallback, AfterEachCallback, InvocationInterceptor {

    OpenFeatureAPI api = OpenFeatureAPI.getInstance();

    private static Map<String, Map<String, Flag<?>>> handleExtendedConfiguration(
            ExtensionContext extensionContext, Map<String, Map<String, Flag<?>>> configuration) {
        PioneerAnnotationUtils.findAllEnclosingRepeatableAnnotations(extensionContext, OpenFeature.class)
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
        String defaultDomain = PioneerAnnotationUtils.findClosestEnclosingAnnotation(
                        extensionContext, OpenFeatureDefaultDomain.class)
                .map(OpenFeatureDefaultDomain::value)
                .orElse("");
        PioneerAnnotationUtils.findAllEnclosingRepeatableAnnotations(
                        extensionContext, dev.openfeature.contrib.tools.junitopenfeature.Flag.class)
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
    public void interceptTestMethod(
            Invocation<Void> invocation,
            ReflectiveInvocationContext<Method> invocationContext,
            ExtensionContext extensionContext)
            throws Throwable {
        executeWithNamespace(invocation, extensionContext);
    }

    @Override
    public void interceptTestTemplateMethod(
            Invocation<Void> invocation,
            ReflectiveInvocationContext<Method> invocationContext,
            ExtensionContext extensionContext)
            throws Throwable {
        executeWithNamespace(invocation, extensionContext);
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) throws Exception {}

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        Map<String, Map<String, Flag<?>>> configuration = handleSimpleConfiguration(extensionContext);
        configuration.putAll(handleExtendedConfiguration(extensionContext, configuration));

        for (Map.Entry<String, Map<String, Flag<?>>> stringMapEntry : configuration.entrySet()) {

            if (!stringMapEntry.getKey().isEmpty()) {
                String domain = stringMapEntry.getKey();
                if (api.getProvider(domain) instanceof TestProvider && api.getProvider(domain) != api.getProvider()) {
                    ((TestProvider) api.getProvider(domain))
                            .addConfigurationForTest(getNamespace(extensionContext), stringMapEntry.getValue());
                } else {
                    api.setProviderAndWait(
                            domain, new TestProvider(getNamespace(extensionContext), stringMapEntry.getValue()));
                }
            } else {
                if (api.getProvider() instanceof TestProvider) {
                    ((TestProvider) api.getProvider())
                            .addConfigurationForTest(getNamespace(extensionContext), stringMapEntry.getValue());
                } else {
                    api.setProviderAndWait(new TestProvider(getNamespace(extensionContext), stringMapEntry.getValue()));
                }
            }
        }

        getStore(extensionContext).put("config", configuration);
    }

    private ExtensionContext.Namespace getNamespace(ExtensionContext extensionContext) {
        return ExtensionContext.Namespace.create(getClass(), extensionContext.getRequiredTestMethod());
    }

    private ExtensionContext.Store getStore(ExtensionContext context) {
        return context.getStore(ExtensionContext.Namespace.create(getClass()));
    }

    private void executeWithNamespace(Invocation<Void> invocation, ExtensionContext extensionContext) throws Throwable {
        TestProvider.setCurrentNamespace(getNamespace(extensionContext));
        try {
            invocation.proceed();
        } finally {
            TestProvider.clearCurrentNamespace();
        }
    }
}
