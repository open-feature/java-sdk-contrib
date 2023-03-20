package dev.openfeature.contrib.providers.inlineeval;

import dev.openfeature.sdk.EvaluationContext;
import lombok.SneakyThrows;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;

public class FileBasedFetcher implements RuleFetcher {
    JSONObject rules;

    public FileBasedFetcher(URI filename) throws IOException {
        String jsonData = String.join("", Files.readAllLines(Paths.get(filename)));
        rules = new JSONObject(jsonData);
    }

    @Override
    public String getRuleForKey(String key) {
        return rules.getJSONObject(key).toString();
    }

    @Override public void initialize(EvaluationContext initialContext) {}
}
