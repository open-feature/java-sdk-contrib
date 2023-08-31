package dev.openfeature.contrib.providers.flagd.resolver.process.storage;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;

public class StringParser extends StdDeserializer<String> {

    public StringParser() {
        super(String.class);
    }

    @Override public String deserialize(JsonParser p, DeserializationContext ctxt)
            throws IOException {

        TreeNode node = p.readValueAsTree();
        return node.toString();
    }
}
