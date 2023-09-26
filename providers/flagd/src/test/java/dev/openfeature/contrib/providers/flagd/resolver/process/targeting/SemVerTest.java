package dev.openfeature.contrib.providers.flagd.resolver.process.targeting;

import io.github.jamsesso.jsonlogic.evaluator.JsonLogicEvaluationException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

class SemVerTest {

    @Test
    public void eqOperator() throws JsonLogicEvaluationException {
        // given
        final SemVer semVer = new SemVer();

        // when
        Object result = semVer.evaluate(Arrays.asList("v1.2.3", "=", "1.2.3"), new Object());

        // then
        if (!(result instanceof Boolean)) {
            fail("Result is not of type Boolean");
        }

        assertThat((Boolean) result).isTrue();
    }

    @Test
    public void neqOperator() throws JsonLogicEvaluationException {
        // given
        final SemVer semVer = new SemVer();

        // when
        Object result = semVer.evaluate(Arrays.asList("v1.2.3", "!=", "1.2.4"), new Object());

        // then
        if (!(result instanceof Boolean)) {
            fail("Result is not of type Boolean");
        }

        assertThat((Boolean) result).isTrue();
    }

    @Test
    public void ltOperator() throws JsonLogicEvaluationException {
        // given
        final SemVer semVer = new SemVer();

        // when
        Object result = semVer.evaluate(Arrays.asList("v1.2.3", "<", "1.2.4"), new Object());

        // then
        if (!(result instanceof Boolean)) {
            fail("Result is not of type Boolean");
        }

        assertThat((Boolean) result).isTrue();
    }

    @Test
    public void lteOperator() throws JsonLogicEvaluationException {
        // given
        final SemVer semVer = new SemVer();

        // when
        Object result = semVer.evaluate(Arrays.asList("v1.2.3", "<=", "1.2.3"), new Object());

        // then
        if (!(result instanceof Boolean)) {
            fail("Result is not of type Boolean");
        }

        assertThat((Boolean) result).isTrue();
    }

    @Test
    public void gtOperator() throws JsonLogicEvaluationException {
        // given
        final SemVer semVer = new SemVer();

        // when
        Object result = semVer.evaluate(Arrays.asList("v1.2.3", ">", "0.2.3"), new Object());

        // then
        if (!(result instanceof Boolean)) {
            fail("Result is not of type Boolean");
        }

        assertThat((Boolean) result).isTrue();
    }

    @Test
    public void gteOperator() throws JsonLogicEvaluationException {
        // given
        final SemVer semVer = new SemVer();

        // when
        Object result = semVer.evaluate(Arrays.asList("v1.2.3", ">=", "v1.2.3"), new Object());

        // then
        if (!(result instanceof Boolean)) {
            fail("Result is not of type Boolean");
        }

        assertThat((Boolean) result).isTrue();
    }

    @Test
    public void majorCompOperator() throws JsonLogicEvaluationException {
        // given
        final SemVer semVer = new SemVer();

        // when
        Object result = semVer.evaluate(Arrays.asList("v1.2.3", "^", "v1.0.0"), new Object());

        // then
        if (!(result instanceof Boolean)) {
            fail("Result is not of type Boolean");
        }

        assertThat((Boolean) result).isTrue();
    }

    @Test
    public void minorCompOperator() throws JsonLogicEvaluationException {
        // given
        final SemVer semVer = new SemVer();

        // when
        Object result = semVer.evaluate(Arrays.asList("v5.0.3", "~", "v5.0.8"), new Object());

        // then
        if (!(result instanceof Boolean)) {
            fail("Result is not of type Boolean");
        }

        assertThat((Boolean) result).isTrue();
    }

    @Test
    public void invalidType() throws JsonLogicEvaluationException {
        // given
        final SemVer semVer = new SemVer();

        // when
        Object result = semVer.evaluate(Arrays.asList("1.2.3", "=", 1.2), new Object());

        assertThat(result).isNull();
    }


    @Test
    public void invalidArg1() throws JsonLogicEvaluationException {
        // given
        final SemVer semVer = new SemVer();

        // when
        Object result = semVer.evaluate(Arrays.asList("1.2", "=", "1.2.3"), new Object());

        assertThat(result).isNull();
    }

    @Test
    public void invalidArg2() throws JsonLogicEvaluationException {
        // given
        final SemVer semVer = new SemVer();

        // when
        Object result = semVer.evaluate(Arrays.asList("1.2.3", "*", "1.2.3"), new Object());

        assertThat(result).isNull();
    }

    @Test
    public void invalidArg3() throws JsonLogicEvaluationException {
        // given
        final SemVer semVer = new SemVer();

        // when
        Object result = semVer.evaluate(Arrays.asList("1.2.3", "=", "1.2"), new Object());

        assertThat(result).isNull();
    }

}