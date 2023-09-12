package dev.openfeature.contrib.providers.flagd.resolver.process.targeting;

import io.github.jamsesso.jsonlogic.evaluator.JsonLogicEvaluationException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.openfeature.contrib.providers.flagd.resolver.process.targeting.Operator.FLAG_KEY;
import static dev.openfeature.contrib.providers.flagd.resolver.process.targeting.Operator.TARGET_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FractionalTest {

    @Test void selfContainedFractional() throws JsonLogicEvaluationException {
        // given
        Fractional fractional = new Fractional();

        /* Rule
         *     [
         *       "bucketKey",
         *       [
         *         "red",
         *         50
         *       ],
         *       [
         *         "blue",
         *         50
         *       ]
         *     ]
         * */

        final List<Object> rule = new ArrayList<>();
        rule.add("bucketKey");

        final List<Object> bucket1 = new ArrayList<>();
        bucket1.add("red");
        bucket1.add(50);

        final List<Object> bucket2 = new ArrayList<>();
        bucket2.add("green");
        bucket2.add(50);

        rule.add(bucket1);
        rule.add(bucket2);

        Map<String, String> data = new HashMap<>();
        data.put(FLAG_KEY, "flagA");

        // when
        Object evaluate = fractional.evaluate(rule, data);

        // then
        assertEquals("green", evaluate);
    }

    @Test void targetingBackedFractional() throws JsonLogicEvaluationException {
        // given
        Fractional fractional = new Fractional();

        /* Rule
         *     [
         *       [
         *         "blue",
         *         50
         *       ],
         *       [
         *         "green",
         *         50
         *       ]
         *     ]
         * */

        final List<Object> rule = new ArrayList<>();

        final List<Object> bucket1 = new ArrayList<>();
        bucket1.add("blue");
        bucket1.add(50);

        final List<Object> bucket2 = new ArrayList<>();
        bucket2.add("green");
        bucket2.add(50);

        rule.add(bucket1);
        rule.add(bucket2);

        Map<String, String> data = new HashMap<>();
        data.put(FLAG_KEY, "headerColor");
        data.put(TARGET_KEY, "foo@foo.com");

        // when
        Object evaluate = fractional.evaluate(rule, data);

        // then
        assertEquals("blue", evaluate);
    }

}