package dev.openfeature.contrib.providers.jsonlogic;

import dev.openfeature.sdk.EvaluationContext;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Logger;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * A {@link RuleFetcher} which reads in the rules from a file. It assumes that the keys are the flag keys and the
 * values are the json logic rules.
 */
@SuppressFBWarnings(
        value = "PATH_TRAVERSAL_IN",
        justification = "This is expected to read files based on user input"
)
public class FileBasedFetcher implements RuleFetcher {
    private final Logger log;
    JSONObject rules;

    /**
     * Create a file based fetcher give a file URI.
     * @param filename URI to a given file.
     * @throws IOException when we can't load the file correctly
     */
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

    @Override public void initialize(EvaluationContext initialContext) {
    }
}
