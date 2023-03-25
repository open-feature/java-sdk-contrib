package dev.openfeature.contrib.providers.jsonlogic;

import dev.openfeature.sdk.EvaluationContext;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Logger;

public class FileBasedFetcher implements RuleFetcher {
    private final Logger log;
    JSONObject rules;

    public FileBasedFetcher(URI filename) throws IOException {
        this.log = Logger.getLogger(String.valueOf(FileBasedFetcher.class));
        String jsonData = String.join("", Files.readAllLines(Paths.get(filename)));
        rules = new JSONObject(jsonData);
    }

    @Override
    public String getRuleForKey(String key) {
        try {
            return rules.getJSONObject(key).toString();
        } catch (JSONException e) {
            log.warning(String.format("Unable to deserialize rule for %s due to exception %s", key, e));
        }
        return null;
    }

    @Override public void initialize(EvaluationContext initialContext) {}
}
