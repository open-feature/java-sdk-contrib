package dev.openfeature.contrib.providers.flagd.resolver.process.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.extern.java.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * flagd feature flag configuration parser.
 */
@Log
@SuppressFBWarnings(value = {"EI_EXPOSE_REP"},
        justification = "Feature flag comes as a Json configuration, hence they must be exposed")
public class FlagParser {
    private static final String FLAG_KEY = "flags";
    private static final String EVALUATOR_KEY = "$evaluators";
    private static final String REPLACER_FORMAT = "\"\\$ref\":(\\s)*\"%s\"";
    private static final String SCHEMA_RESOURCE = "flagd-definitions.json";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Map<String, Pattern> PATTERN_MAP = new HashMap<>();
    private static final Pattern REG_BRACKETS = Pattern.compile("^[^{]*\\{|}[^}]*$");

    private static JsonSchema SCHEMA_VALIDATOR;

    static {
        try (InputStream schema = FlagParser.class.getClassLoader().getResourceAsStream(SCHEMA_RESOURCE)) {
            if (schema == null) {
                log.log(Level.WARNING, String.format("Resource %s not found", SCHEMA_RESOURCE));
            } else {
                final ByteArrayOutputStream result = new ByteArrayOutputStream();
                byte[] buffer = new byte[512];
                for (int size; 0 < (size = schema.read(buffer)); ) {
                    result.write(buffer, 0, size);
                }

                JsonSchemaFactory instance = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
                SCHEMA_VALIDATOR = instance.getSchema(result.toString());
            }
        } catch (Throwable e) {
            // log only, do not throw
            log.log(Level.WARNING,
                    String.format("Error loading resource %s, schema validation will be skipped", SCHEMA_RESOURCE), e);
        }
    }

    /**
     * Parse {@link String} for feature flags.
     */
    public static Map<String, FeatureFlag> parseString(final String configuration) throws IOException {
        if (SCHEMA_VALIDATOR != null) {
            try (JsonParser parser = MAPPER.createParser(configuration)) {
                Set<ValidationMessage> validationMessages = SCHEMA_VALIDATOR.validate(parser.readValueAsTree());

                if (!validationMessages.isEmpty()) {
                    throw new IllegalArgumentException(
                            String.format("Failed to parse configurations. %d validation error(s) reported.",
                                    validationMessages.size()));
                }
            }
        }

        final String transposedConfiguration = transposeEvaluators(configuration);

        final Map<String, FeatureFlag> flagMap = new HashMap<>();

        try (JsonParser parser = MAPPER.createParser(transposedConfiguration)) {
            final TreeNode treeNode = parser.readValueAsTree();
            final TreeNode flagNode = treeNode.get(FLAG_KEY);

            if (flagNode == null) {
                throw new IllegalArgumentException("No flag configurations found in the payload");
            }

            final Iterator<String> it = flagNode.fieldNames();
            while (it.hasNext()) {
                final String key = it.next();
                flagMap.put(key, MAPPER.treeToValue(flagNode.get(key), FeatureFlag.class));
            }
        }

        return flagMap;
    }

    private static String transposeEvaluators(final String configuration) throws IOException {
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
                final String replacer = REG_BRACKETS.matcher(evaluators.get(evalName).toString()).replaceAll("");

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
