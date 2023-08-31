package dev.openfeature.contrib.providers.flagd.resolver.process.storage;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.resolver.process.storage.connector.GrpcStreamConnector;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;


public class FlagStore {
    private static final String FLAG_KEY = "flags";
    private static final String EVALUATOR_KEY = "$evaluators";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Map<String, Pattern> PATTERN_MAP = new HashMap<>();
    private static final Pattern REG_BRACKETS = Pattern.compile("^[^{]*\\{|}[^}]*$");

    private final Map<String, FlagModel> flags = new HashMap<>();
    private FlagdOptions options;

    public FlagStore(final FlagdOptions options) {
        this.options = options;
    }

    public void init() {
        // todo switch based on options ?
        GrpcStreamConnector connector = new GrpcStreamConnector(options);
        connector.init(this::setFlags);
    }

    public void setFlags(final String configuration) {
        final String transposedConfiguration = transposeEvaluators(configuration);

        // then convert to flags
        try (JsonParser parser = MAPPER.createParser(transposedConfiguration)) {
            final TreeNode treeNode = parser.readValueAsTree();
            final TreeNode flagNode = treeNode.get(FLAG_KEY);

            final Iterator<String> it = flagNode.fieldNames();
            while (it.hasNext()) {
                final String key = it.next();
                flags.put(key, MAPPER.treeToValue(flagNode.get(key), FlagModel.class));
            }
        } catch (IOException e) {
            // todo handle errors
            throw new RuntimeException(e);
        }
    }

    public FlagModel getFLag(final String key) {
        return flags.get(key);
    }

    static String transposeEvaluators(final String configuration) {
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

                final String replacePattern = String.format("\"\\$ref\":(\\s)*\"%s\"", evalName);

                // then derive pattern
                final Pattern reg_replace =
                        PATTERN_MAP.computeIfAbsent(replacePattern, s -> Pattern.compile(replacePattern));

                // finally replace all references
                replacedConfigurations = reg_replace.matcher(replacedConfigurations).replaceAll(replacer);
            }

            return replacedConfigurations;
        } catch (IOException e) {
            // todo handle errors
            throw new RuntimeException(e);
        }
    }

}
