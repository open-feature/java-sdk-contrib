package dev.openfeature.contrib.providers.flagd.resolver.process.targeting;

import static dev.openfeature.contrib.providers.flagd.resolver.process.targeting.Operator.FLAGD_PROPS_KEY;
import static dev.openfeature.contrib.providers.flagd.resolver.process.targeting.Operator.FLAG_KEY;
import static dev.openfeature.contrib.providers.flagd.resolver.process.targeting.Operator.TARGET_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.jamsesso.jsonlogic.evaluator.JsonLogicEvaluationException;

class FractionalTest {

    @Test
    void selfContainedFractionalA() throws JsonLogicEvaluationException {
        // given
        Fractional fractional = new Fractional();

        /* Rule
         *     [
         *       "flagAbucketKeyA", // this is resolved value of an expression
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
        rule.add("flagAbucketKeyA");

        final List<Object> bucket1 = new ArrayList<>();
        bucket1.add("red");
        bucket1.add(50);

        final List<Object> bucket2 = new ArrayList<>();
        bucket2.add("green");
        bucket2.add(50);

        rule.add(bucket1);
        rule.add(bucket2);

        Map<String, String> flagdProperties = new HashMap<>();
        flagdProperties.put(FLAG_KEY, "flagA");
        Map<String, Object> data = new HashMap<>();
        data.put(FLAGD_PROPS_KEY, flagdProperties);

        // when
        Object evaluate = fractional.evaluate(rule, data);

        // then
        assertEquals("red", evaluate);
    }

    @Test
    void selfContainedFractionalB() throws JsonLogicEvaluationException {
        // given
        Fractional fractional = new Fractional();

        /* Rule
         *     [
         *       "flagAbucketKeyB", // this is resolved value of an expression
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
        rule.add("flagAbucketKeyB");

        final List<Object> bucket1 = new ArrayList<>();
        bucket1.add("red");
        bucket1.add(50);

        final List<Object> bucket2 = new ArrayList<>();
        bucket2.add("green");
        bucket2.add(50);

        rule.add(bucket1);
        rule.add(bucket2);

        Map<String, String> flagdProperties = new HashMap<>();
        flagdProperties.put(FLAG_KEY, "flagA");
        Map<String, Object> data = new HashMap<>();
        data.put(FLAGD_PROPS_KEY, flagdProperties);

        // when
        Object evaluate = fractional.evaluate(rule, data);

        // then
        assertEquals("green", evaluate);
    }

    @Test
    void targetingBackedFractional() throws JsonLogicEvaluationException {
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


    @Test
    void invalidRuleSumNot100() throws JsonLogicEvaluationException {
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
         *         70
         *       ]
         *     ]
         * */

        final List<Object> rule = new ArrayList<>();

        final List<Object> bucket1 = new ArrayList<>();
        bucket1.add("blue");
        bucket1.add(50);

        final List<Object> bucket2 = new ArrayList<>();
        bucket2.add("green");
        bucket2.add(70);

        rule.add(bucket1);
        rule.add(bucket2);

        Map<String, String> data = new HashMap<>();
        data.put(FLAG_KEY, "headerColor");
        data.put(TARGET_KEY, "foo@foo.com");

        // when
        Object evaluate = fractional.evaluate(rule, data);

        // then
        assertNull(evaluate);
    }

    @Test
    void notEnoughBuckets() throws JsonLogicEvaluationException {
        // given
        Fractional fractional = new Fractional();

        /* Rule
         *     [
         *       [
         *         "blue",
         *         100
         *       ]
         *     ]
         * */

        final List<Object> rule = new ArrayList<>();

        final List<Object> bucket1 = new ArrayList<>();
        bucket1.add("blue");
        bucket1.add(50);

        rule.add(bucket1);

        Map<String, String> data = new HashMap<>();
        data.put(FLAG_KEY, "headerColor");
        data.put(TARGET_KEY, "foo@foo.com");

        // when
        Object evaluate = fractional.evaluate(rule, data);

        // then
        assertNull(evaluate);
    }


    @Test
    void invalidRule() throws JsonLogicEvaluationException {
        // given
        Fractional fractional = new Fractional();

        /* Rule
         *     [
         *       [
         *         "blue",
         *         50
         *       ],
         *       [
         *         "green"
         *       ]
         *     ]
         * */

        final List<Object> rule = new ArrayList<>();

        final List<Object> bucket1 = new ArrayList<>();
        bucket1.add("blue");
        bucket1.add(50);

        final List<Object> bucket2 = new ArrayList<>();
        bucket2.add("green");

        rule.add(bucket1);
        rule.add(bucket2);

        Map<String, String> data = new HashMap<>();
        data.put(FLAG_KEY, "headerColor");
        data.put(TARGET_KEY, "foo@foo.com");

        // when
        Object evaluate = fractional.evaluate(rule, data);

        // then
        assertNull(evaluate);
    }

}