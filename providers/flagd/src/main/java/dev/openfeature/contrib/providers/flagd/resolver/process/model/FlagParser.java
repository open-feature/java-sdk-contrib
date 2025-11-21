package dev.openfeature.contrib.providers.flagd.resolver.process.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/** flagd feature flag configuration parser. */
@Slf4j
public class FlagParser {
    private static final String FLAG_KEY = "flags";
    private static final String METADATA_KEY = "metadata";
    private static final String EVALUATOR_KEY = "$evaluators";
    private static final String REPLACER_FORMAT = "\"\\$ref\":(\\s)*\"%s\"";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static JsonSchema SCHEMA_VALIDATOR;

    private FlagParser() {}

    static {
        try {
            // load both schemas from resources (root (flags.json) and referenced (targeting.json)
            // we don't want to resolve anything from the network
            Map<String, String> mappings = new HashMap<>();
            mappings.put("https://flagd.dev/schema/v0/targeting.json", "classpath:flagd/schemas/targeting.json");
            mappings.put("https://flagd.dev/schema/v0/flags.json", "classpath:flagd/schemas/flags.json");

            SCHEMA_VALIDATOR = JsonSchemaFactory.getInstance(
                            SpecVersion.VersionFlag.V7,
                            builder -> builder.schemaMappers(schemaMappers -> schemaMappers.mappings(mappings)))
                    .getSchema(new URI("https://flagd.dev/schema/v0/flags.json"));
        } catch (Throwable e) {
            // log only, do not throw
            log.warn(String.format("Error loading schema resources, schema validation will be skipped"));
        }
    }

    /** Parse {@link String} for feature flags. */
    public static ParsingResult parseString(final String configuration, boolean throwIfInvalid) throws IOException {
        if (SCHEMA_VALIDATOR != null) {
            try (JsonParser parser = MAPPER.createParser(configuration)) {
                Set<ValidationMessage> validationMessages = SCHEMA_VALIDATOR.validate(parser.readValueAsTree());

                if (!validationMessages.isEmpty()) {
                    List<String> distinctMessages = validationMessages.stream()
                            .map(ValidationMessage::toString)
                            .distinct()
                            .collect(Collectors.toList());
                    String message = String.format("Invalid flag configuration: %s", distinctMessages);
                    log.warn(message);
                    if (throwIfInvalid) {
                        throw new IllegalArgumentException(message);
                    }
                }
            }
        }

        final String transposedConfiguration = transposeEvaluators(configuration);

        final Map<String, FeatureFlag> flagMap = new HashMap<>();
        final Map<String, Object> flagSetMetadata;
        try (JsonParser parser = MAPPER.createParser(transposedConfiguration)) {
            final TreeNode treeNode = parser.readValueAsTree();
            final TreeNode flagNode = treeNode.get(FLAG_KEY);
            final TreeNode metadataNode = treeNode.get(METADATA_KEY);
            flagSetMetadata = parseMetadata(metadataNode);

            if (flagNode == null) {
                throw new IllegalArgumentException("No flag configurations found in the payload");
            }

            final Iterator<String> it = flagNode.fieldNames();
            while (it.hasNext()) {
                final String key = it.next();
                flagMap.put(key, MAPPER.treeToValue(flagNode.get(key), FeatureFlag.class));
            }
        }

        return new ParsingResult(flagMap, flagSetMetadata);
    }

    private static Map<String, Object> parseMetadata(TreeNode metadataNode) throws JsonProcessingException {
        if (metadataNode == null) {
            return new HashMap<>();
        }

        TypeReference<Map<String, Object>> typeRef = new TypeReference<Map<String, Object>>() {};
        return MAPPER.treeToValue(metadataNode, typeRef);
    }

    private static String transposeEvaluators(final String configuration) throws IOException {
        try (JsonParser parser = MAPPER.createParser(configuration)) {
            final Map<String, Pattern> patternMap = new HashMap<>();
            final TreeNode treeNode = parser.readValueAsTree();
            final TreeNode evaluators = treeNode.get(EVALUATOR_KEY);

            if (evaluators == null || evaluators.size() == 0) {
                return configuration;
            }

            String replacedConfigurations = configuration;
            final Iterator<String> evalFields = evaluators.fieldNames();

            while (evalFields.hasNext()) {
                final String evalName = evalFields.next();
                // first replace outmost brackets
                final String evaluator = evaluators.get(evalName).toString();
                final String replacer = evaluator.substring(1, evaluator.length() - 1);

                final String replacePattern = String.format(REPLACER_FORMAT, evalName);

                // then derive pattern
                final Pattern reg_replace =
                        patternMap.computeIfAbsent(replacePattern, s -> Pattern.compile(replacePattern));

                // finally replace all references
                replacedConfigurations =
                        reg_replace.matcher(replacedConfigurations).replaceAll(replacer);
            }

            return replacedConfigurations;
        }
    }
}
