package dev.openfeature.contrib.tools.flagd.core.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;

/**
 * Custom serializer to preserve Json node as a {@link String}.
 */
class StringSerializer extends StdDeserializer<String> {

    /**
     * Construct the string serializer.
     */
    public StringSerializer() {
        super(String.class);
    }

    @Override
    public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        TreeNode node = p.readValueAsTree();
        return node.toString();
    }
}
