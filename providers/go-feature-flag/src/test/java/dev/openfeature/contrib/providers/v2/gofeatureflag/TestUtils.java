package dev.openfeature.contrib.providers.v2.gofeatureflag;

import dev.openfeature.sdk.MutableContext;
import dev.openfeature.sdk.MutableStructure;
import dev.openfeature.sdk.Value;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class TestUtils {
    public static final MutableContext defaultEvaluationContext = getDefaultEvaluationContext();

    private static MutableContext getDefaultEvaluationContext() {
        MutableContext context = new MutableContext();
        context.setTargetingKey("d45e303a-38c2-11ed-a261-0242ac120002");
        context.add("email", "john.doe@gofeatureflag.org");
        context.add("firstname", "john");
        context.add("lastname", "doe");
        context.add("anonymous", false);
        context.add("professional", true);
        context.add("rate", 3.14);
        context.add("age", 30);
        context.add(
                "company_info", new MutableStructure().add("name", "my_company").add("size", 120));
        List<Value> labels = new ArrayList<>();
        labels.add(new Value("pro"));
        labels.add(new Value("beta"));
        context.add("labels", labels);
        return context;
    }

    public static String readMockResponse(String dir, String filename) throws Exception {
        URL url = TestUtils.class.getClassLoader().getResource(dir + filename);
        assert url != null;
        byte[] bytes = Files.readAllBytes(Paths.get(url.toURI()));
        return new String(bytes);
    }
}
