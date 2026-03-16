package dev.openfeature.contrib.tools.flagd.core.targeting;

import static dev.openfeature.contrib.tools.flagd.core.targeting.Operator.FLAGD_PROPS_KEY;
import static dev.openfeature.contrib.tools.flagd.core.targeting.Operator.FLAG_KEY;
import static dev.openfeature.contrib.tools.flagd.core.targeting.Operator.TARGET_KEY;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jamsesso.jsonlogic.JsonLogicException;
import io.github.jamsesso.jsonlogic.evaluator.JsonLogicEvaluationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.converter.ArgumentConversionException;
import org.junit.jupiter.params.converter.ConvertWith;
import org.junit.jupiter.params.converter.TypedArgumentConverter;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

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

    @ParameterizedTest
    @ValueSource(ints = {0, 1, -1, Integer.MAX_VALUE, Integer.MAX_VALUE - 1, Integer.MIN_VALUE, Integer.MIN_VALUE + 1})
    void edgeCasesDoNotThrow(int hash) throws JsonLogicException {
        int totalWeight = 8;
        int buckets = 4;
        List<Fractional.FractionProperty> bucketsList = new ArrayList<>(buckets);
        for (int i = 0; i < buckets; i++) {
            bucketsList.add(new Fractional.FractionProperty(List.of("bucket" + i, totalWeight / buckets), ""));
        }

        AtomicReference<String> result = new AtomicReference<>();
        assertDoesNotThrow(() -> result.set(Fractional.distributeValueFromHash(hash, bucketsList, totalWeight, "")));

        assertNotNull(result.get());
        assertTrue(result.get().startsWith("bucket"));
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
}
