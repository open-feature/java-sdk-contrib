package dev.openfeature.contrib.tools.junitopenfeature;

import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.providers.memory.Flag;
import java.lang.reflect.Method;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
        List<OpenFeature> openFeatureAnnotationList = PioneerAnnotationUtils.findAllEnclosingRepeatableAnnotations(
                        extensionContext, OpenFeature.class)
                .collect(Collectors.toList());
        Map<String, Set<String>> nonTypedFlagNamesByDomain = getFlagNamesByDomain(openFeatureAnnotationList);
        openFeatureAnnotationList.forEach(annotation -> {
            Map<String, Flag<?>> domainFlags = configuration.getOrDefault(annotation.domain(), new HashMap<>());

            Arrays.stream(annotation.value())
                    .filter(flag -> !domainFlags.containsKey(flag.name()))
                    .forEach(flag -> {
                        Flag.FlagBuilder<?> builder = generateFlagBuilder(flag);
                        domainFlags.put(flag.name(), builder.build());
                    });
            addTypedFlags(
                    annotation,
                    domainFlags,
                    nonTypedFlagNamesByDomain.getOrDefault(annotation.domain(), new HashSet<>()));
            configuration.put(annotation.domain(), domainFlags);
        });
        return configuration;
    }

    private static Map<String, Set<String>> getFlagNamesByDomain(List<OpenFeature> openFeatureList) {
        return openFeatureList.stream()
                .map(o -> {
                    Set<String> flagNames = Arrays.stream(o.value())
                            .map(dev.openfeature.contrib.tools.junitopenfeature.Flag::name)
                            .collect(Collectors.toSet());
                    return new AbstractMap.SimpleEntry<>(o.domain(), flagNames);
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (t1, t2) -> {
                    t1.addAll(t2);
                    return t1;
                }));
    }

    private static void addTypedFlags(OpenFeature annotation, Map<String, Flag<?>> domainFlags, Set<String> flagNames) {
        addBooleanFlags(Arrays.stream(annotation.booleanFlags()), domainFlags, flagNames);
        addStringFlags(Arrays.stream(annotation.stringFlags()), domainFlags, flagNames);
        addIntegerFlags(Arrays.stream(annotation.integerFlags()), domainFlags, flagNames);
        addDoubleFlags(Arrays.stream(annotation.doubleFlags()), domainFlags, flagNames);
    }

    private static void addBooleanFlags(
            Stream<BooleanFlag> booleanFlags, Map<String, Flag<?>> domainFlags, Set<String> flagNames) {

        booleanFlags.forEach(flag -> addFlag(domainFlags, flagNames, flag.name(), flag.value()));
    }

    private static void addStringFlags(
            Stream<StringFlag> stringFlags, Map<String, Flag<?>> domainFlags, Set<String> flagNames) {
        stringFlags.forEach(flag -> addFlag(domainFlags, flagNames, flag.name(), flag.value()));
    }

    private static void addIntegerFlags(
            Stream<IntegerFlag> integerFlags, Map<String, Flag<?>> domainFlags, Set<String> flagNames) {
        integerFlags.forEach(flag -> addFlag(domainFlags, flagNames, flag.name(), flag.value()));
    }

    private static void addDoubleFlags(
            Stream<DoubleFlag> doubleFlags, Map<String, Flag<?>> domainFlags, Set<String> flagNames) {
        doubleFlags.forEach(flag -> addFlag(domainFlags, flagNames, flag.name(), flag.value()));
    }

    private static <T> void addFlag(
            Map<String, Flag<?>> domainFlags, Set<String> domainFlagNames, String flagName, T value) {
        if (domainFlagNames.contains(flagName)) {
            throw new IllegalArgumentException("Flag with name " + flagName + " already exists. "
                    + "There shouldn't be @Flag and @" + value.getClass().getSimpleName() + "Flag with the same name!");
        }

        if (domainFlags.containsKey(flagName)) {
            return;
        }
        Flag.FlagBuilder<Object> builder =
                Flag.builder().variant(String.valueOf(value), value).defaultVariant(String.valueOf(value));
        domainFlags.put(flagName, builder.build());
    }

    private static Map<String, Map<String, Flag<?>>> handleSimpleConfiguration(ExtensionContext extensionContext) {
        Map<String, Map<String, Flag<?>>> configuration = new HashMap<>();
        String defaultDomain = PioneerAnnotationUtils.findClosestEnclosingAnnotation(
                        extensionContext, OpenFeatureDefaultDomain.class)
                .map(OpenFeatureDefaultDomain::value)
                .orElse("");
        Map<String, Flag<?>> domainFlags = configuration.getOrDefault(defaultDomain, new HashMap<>());
        List<dev.openfeature.contrib.tools.junitopenfeature.Flag> flagList =
                PioneerAnnotationUtils.findAllEnclosingRepeatableAnnotations(
                                extensionContext, dev.openfeature.contrib.tools.junitopenfeature.Flag.class)
                        .collect(Collectors.toList());
        Set<String> flagNames = flagList.stream()
                .map(dev.openfeature.contrib.tools.junitopenfeature.Flag::name)
                .collect(Collectors.toSet());

        flagList.forEach(flag -> {
            if (!domainFlags.containsKey(flag.name())) {
                Flag.FlagBuilder<?> builder = generateFlagBuilder(flag);
                domainFlags.put(flag.name(), builder.build());
                configuration.put(defaultDomain, domainFlags);
            }
        });

        Stream<BooleanFlag> booleanFlags =
                PioneerAnnotationUtils.findAllEnclosingRepeatableAnnotations(extensionContext, BooleanFlag.class);
        addBooleanFlags(booleanFlags, domainFlags, flagNames);

        Stream<StringFlag> stringFlags =
                PioneerAnnotationUtils.findAllEnclosingRepeatableAnnotations(extensionContext, StringFlag.class);
        addStringFlags(stringFlags, domainFlags, flagNames);

        Stream<IntegerFlag> integerFlags =
                PioneerAnnotationUtils.findAllEnclosingRepeatableAnnotations(extensionContext, IntegerFlag.class);
        addIntegerFlags(integerFlags, domainFlags, flagNames);

        Stream<DoubleFlag> doubleFlags =
                PioneerAnnotationUtils.findAllEnclosingRepeatableAnnotations(extensionContext, DoubleFlag.class);
        addDoubleFlags(doubleFlags, domainFlags, flagNames);

        if (!domainFlags.isEmpty()) {
            configuration.put(defaultDomain, domainFlags);
        }

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
