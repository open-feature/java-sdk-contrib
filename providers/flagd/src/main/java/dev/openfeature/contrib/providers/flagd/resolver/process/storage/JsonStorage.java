package dev.openfeature.contrib.providers.flagd.resolver.process.storage;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class JsonStorage {
    public static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, Flag> flags = new HashMap<>();

    public void setFlags(final String configuration) {
        try (JsonParser parser = MAPPER.createParser(configuration)) {
            final TreeNode treeNode = parser.readValueAsTree();

            TreeNode flagNode = treeNode.get("flags");
            for (Iterator<String> it = flagNode.fieldNames(); it.hasNext(); ) {
                final String key = it.next();
                flags.put(key, MAPPER.treeToValue(flagNode.get(key), Flag.class));
            }


        } catch (IOException e) {
            // todo handle errors
            throw new RuntimeException(e);
        }

    }

}
