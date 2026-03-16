package dev.openfeature.contrib.tools.flagd.core.targeting;

import static dev.openfeature.contrib.tools.flagd.core.targeting.Operator.*;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.codec.digest.MurmurHash3;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
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

    @Test
    void b(){
        byte[] bytes =  new byte[]{49, 71, 65, 71, 87, 114, 90, 89, 104, 67, 75, 74, 69, 52, 78, 114, 67, 86, 118, 114, 99, 49, 90, 65, 110, 115, 57, 87, 86, 117, 57, 114, 70, 101, 112, 83, 102, 83, 117, 85, 69, 71, 97, 122, 111, 80, 67, 52, 121, 99, 119, 90, 87, 98, 113, 66, 84, 106, 122, 49, 57, 74, 117, 87, 71, 98, 77, 99, 107, 89, 110, 88, 72, 78, 57, 86, 102, 55, 53, 113, 89, 121, 57, 74, 51, 54, 115, 77, 86, 108, 73, 73, 122, 66, 120, 53, 66, 70, 76, 51, 69, 55, 118, 117, 56, 53, 68, 112, 116, 70, 120, 65, 67, 122, 77, 72, 48, 98, 112, 100, 117, 88, 88, 80, 54, 118, 65, 90};
        String s = new String(bytes);
        System.out.println(s);
        System.out.println(MurmurHash3.hash32x86(s.getBytes(), 0, s.getBytes().length, 0));
    }

    @Test
    void c(){
        /*
        - Single character: `"i"` → hash 2165993515, bucket_value 50 (first of upper bucket in 50/50)
  - Two characters: `"bx"` → hash 2106591975, bucket_value 49 (last of lower bucket in 50/50)
  - Two characters: `"cd"` → hash 2158755732, bucket_value 50 (first of upper bucket)
         */
        System.out.println(Integer.toUnsignedLong(MurmurHash3.hash32x86("i".getBytes(), 0, "i".getBytes().length, 0)));
        System.out.println(Integer.toUnsignedLong(MurmurHash3.hash32x86("bx".getBytes(), 0, "bx".getBytes().length, 0)));
        System.out.println(Integer.toUnsignedLong(MurmurHash3.hash32x86("cd".getBytes(), 0, "cd".getBytes().length, 0)));
    }

    @Test
    void d() throws JsonLogicException {
        int buckets = 2;
        int totalWeight = 100;
        int weight = totalWeight / buckets;
        List<Fractional.FractionProperty> bucketsList = new ArrayList<>(buckets);
        for (int i = 0; i < buckets - 1; i++) {
            bucketsList.add(new Fractional.FractionProperty(List.of("" + i, weight), ""));
        }
        bucketsList.add(
                new Fractional.FractionProperty(List.of("" + (buckets - 1), totalWeight - weight * (buckets - 1)), ""));

        Fractional.distributeValueFromHash((int)2165993515L,bucketsList, totalWeight, "");
    }

    @Test
    void a() {
        Random random = new Random(0);
        System.out.println("starting");
        boolean min = false;
        boolean max = false;
        boolean minusOne = false;
        boolean plusOne = false;
        boolean zero = false;
        while (!min || !max || !minusOne || !plusOne || !zero) {
            byte[] bytes = RandomStringUtils.random(128, '!', '}', true, true, null, random).getBytes();

            int mmrHash = MurmurHash3.hash32x86(bytes, 0, bytes.length, 0);
            if (mmrHash == Integer.MIN_VALUE) {
                System.out.println("c is min value = " + new String(bytes));
                min = true;
            } else if (mmrHash == Integer.MAX_VALUE) {
                max = true;
                System.out.println("c is max value = " + new String(bytes));
            } else if (mmrHash == -1) {
                minusOne = true;
                System.out.println("c is -1 = " + new String(bytes));
            } else if (mmrHash == 1) {
                plusOne = true;
                System.out.println("c is 1 = " + new String(bytes));
            } else if (mmrHash == 0) {
                zero = true;
                System.out.println("c is 0 = " + new String(bytes));
            }
        }
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

    @Test
    void statistics() throws JsonLogicException {
        int totalWeight = Integer.MAX_VALUE;
        int buckets = 16;
        int[] hits = new int[buckets];
        List<Fractional.FractionProperty> bucketsList = new ArrayList<>(buckets);
        int weight = totalWeight / buckets;
        for (int i = 0; i < buckets - 1; i++) {
            bucketsList.add(new Fractional.FractionProperty(List.of("" + i, weight), ""));
        }
        bucketsList.add(
                new Fractional.FractionProperty(List.of("" + (buckets - 1), totalWeight - weight * (buckets - 1)), ""));

        for (long i = Integer.MIN_VALUE; i <= Integer.MAX_VALUE; i += 127) {
            String bucketStr = Fractional.distributeValueFromHash((int) i, bucketsList, totalWeight, "");
            int bucket = Integer.parseInt(bucketStr);
            hits[bucket]++;
        }

        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int i = 0; i < hits.length; i++) {
            int current = hits[i];
            if (current < min) {
                min = current;
            }
            if (current > max) {
                max = current;
            }
        }

        int delta = max - min;
        assertTrue(
                delta < 3,
                "Delta should be less than 3, but was " + delta + ". Distributions: " + Arrays.toString(hits));
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
