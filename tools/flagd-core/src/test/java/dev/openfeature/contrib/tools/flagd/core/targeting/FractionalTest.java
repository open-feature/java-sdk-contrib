package dev.openfeature.contrib.tools.flagd.core.targeting;

import static dev.openfeature.contrib.tools.flagd.core.targeting.Operator.FLAGD_PROPS_KEY;
import static dev.openfeature.contrib.tools.flagd.core.targeting.Operator.FLAG_KEY;
import static dev.openfeature.contrib.tools.flagd.core.targeting.Operator.TARGET_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jamsesso.jsonlogic.evaluator.JsonLogicEvaluationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.converter.ArgumentConversionException;
import org.junit.jupiter.params.converter.ConvertWith;
import org.junit.jupiter.params.converter.TypedArgumentConverter;
import org.junit.jupiter.params.provider.MethodSource;

class FractionalTest {

    @ParameterizedTest
    @MethodSource("allFilesInDir")
    void validate_emptyJson_targetingReturned(@ConvertWith(FileContentConverter.class) TestData testData)
            throws JsonLogicEvaluationException {
        // given
        Fractional fractional = new Fractional();

        Map<String, Object> data = new HashMap<>();
        data.put(FLAG_KEY, "headerColor");
        data.put(TARGET_KEY, "foo@foo.com");

        Map<String, String> flagdProperties = new HashMap<>();
        flagdProperties.put(FLAG_KEY, "flagA");
        data.put(FLAGD_PROPS_KEY, flagdProperties);

        // when
        Object evaluate = fractional.evaluate(testData.rule, data, "path");

        // then
        assertEquals(testData.result, evaluate);
    }

    public static Stream<?> allFilesInDir() throws IOException {
        return Files.list(Paths.get("src", "test", "resources", "fractional"))
                .map(path -> arguments(named(path.getFileName().toString(), path)));
    }

    static class FileContentConverter extends TypedArgumentConverter<Path, TestData> {
        protected FileContentConverter() {
            super(Path.class, TestData.class);
        }

        @Override
        protected TestData convert(Path path) throws ArgumentConversionException {
            try {
                Stream<String> lines = Files.lines(path);
                String data = lines.collect(Collectors.joining("\n"));
                lines.close();
                ObjectMapper mapper = new ObjectMapper();
                return mapper.readValue(data, TestData.class);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    static class TestData {
        @JsonProperty("result")
        Object result;

        @JsonProperty("rule")
        List<Object> rule;
    }

    @Test
    void missingBucketKeyReturnsNull() throws JsonLogicEvaluationException {
        // no targeting key in data; bucket key var resolves to null
        Fractional fractional = new Fractional();

        Map<String, Object> data = new HashMap<>();
        Map<String, String> flagdProperties = new HashMap<>();
        flagdProperties.put(FLAG_KEY, "flagA");
        data.put(FLAGD_PROPS_KEY, flagdProperties);
        // no TARGET_KEY set

        List<Object> rule = List.of(
                // bucket key is a null var result (simulated by being a non-string, non-list)
                List.of("one", 50), List.of("two", 50));

        // bucketing key is null, so fractional falls back to flagKey + targetingKey
        // but targetingKey is null, so it should return null
        assertNull(fractional.evaluate(rule, data, "path"));
    }

    @Test
    void singleEntryFractionalWithNonStringVariant() throws JsonLogicEvaluationException {
        // simulates pre-evaluation flattening of [[100, 1]] -> [100, 1]
        Fractional fractional = new Fractional();

        Map<String, Object> data = new HashMap<>();
        data.put(TARGET_KEY, "user");
        Map<String, String> flagdProperties = new HashMap<>();
        flagdProperties.put(FLAG_KEY, "flagA");
        data.put(FLAGD_PROPS_KEY, flagdProperties);

        List<Object> rule = List.of(100, 1);

        assertEquals(100, fractional.evaluate(rule, data, "path"));
    }

    @Test
    void singleEntryFractionalWithStringVariant() throws JsonLogicEvaluationException {
        // simulates pre-evaluation flattening of [["single", 1]] -> ["single", 1]
        // "single" looks like a bucketing key but is actually the variant
        Fractional fractional = new Fractional();

        Map<String, Object> data = new HashMap<>();
        data.put(TARGET_KEY, "user");
        Map<String, String> flagdProperties = new HashMap<>();
        flagdProperties.put(FLAG_KEY, "flagA");
        data.put(FLAGD_PROPS_KEY, flagdProperties);

        List<Object> rule = List.of("single", 1);

        assertEquals("single", fractional.evaluate(rule, data, "path"));
    }

    @Test
    void zeroWeightsReturnsNull() throws JsonLogicEvaluationException {
        Fractional fractional = new Fractional();

        Map<String, Object> data = new HashMap<>();
        data.put(TARGET_KEY, "user");
        Map<String, String> flagdProperties = new HashMap<>();
        flagdProperties.put(FLAG_KEY, "flagA");
        data.put(FLAGD_PROPS_KEY, flagdProperties);

        List<Object> rule = List.of(List.of("one", 0), List.of("two", 0));

        assertNull(fractional.evaluate(rule, data, "path"));
    }
}
