package dev.openfeature.contrib.providers.flagd.resolver.process.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * flagd feature flag configuration parser.
 */
public class FlagParser {
    private static final String FLAG_KEY = "flags";
    private static final String EVALUATOR_KEY = "$evaluators";
    private static final String REPLACER_FORMAT = "\"\\$ref\":(\\s)*\"%s\"";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Map<String, Pattern> PATTERN_MAP = new HashMap<>();
    private static final Pattern REG_BRACKETS = Pattern.compile("^[^{]*\\{|}[^}]*$");

    /**
     * Parse {@link String} for feature flags.
     */
    public static Map<String, FeatureFlag> parseString(final String configuration) throws IOException {
        final String transposedConfiguration = transposeEvaluators(configuration);

        final Map<String, FeatureFlag> flagMap = new HashMap<>();

        try (JsonParser parser = MAPPER.createParser(transposedConfiguration)) {
            final TreeNode treeNode = parser.readValueAsTree();
            final TreeNode flagNode = treeNode.get(FLAG_KEY);

            final Iterator<String> it = flagNode.fieldNames();
            while (it.hasNext()) {
                final String key = it.next();
                flagMap.put(key, MAPPER.treeToValue(flagNode.get(key), FeatureFlag.class));
            }
        }

        return flagMap;
    }

    static String transposeEvaluators(final String configuration) throws IOException {
        try (JsonParser parser = MAPPER.createParser(configuration)) {
            final TreeNode treeNode = parser.readValueAsTree();
            final TreeNode evaluators = treeNode.get(EVALUATOR_KEY);

            if (evaluators == null || evaluators.size() == 0) {
                return configuration;
            }

            String replacedConfigurations = configuration;
            final Iterator<String> evalFields = evaluators.fieldNames();

            while (evalFields.hasNext()) {
                final String evalName = evalFields.next();
                // first replace brackets
                final String replacer =
                        REG_BRACKETS.matcher(evaluators.get(evalName).toString())
                                .replaceAll("");

                final String replacePattern = String.format(REPLACER_FORMAT, evalName);

                // then derive pattern
                final Pattern reg_replace =
                        PATTERN_MAP.computeIfAbsent(replacePattern, s -> Pattern.compile(replacePattern));

                // finally replace all references
                replacedConfigurations = reg_replace.matcher(replacedConfigurations).replaceAll(replacer);
            }

            return replacedConfigurations;
        }
    }

}
