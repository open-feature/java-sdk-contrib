package dev.openfeature.contrib.providers.jsonlogic;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Utils {
    public static String readTestResource(String name) throws IOException, URISyntaxException {
        URL url = Utils.class.getResource(name);
        if (url == null) {
            return null;
        }
        return String.join("", Files.readAllLines(Paths.get(url.toURI())));
    }
}
