package dev.openfeature.contrib.tools.flagd.core.targeting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import io.github.jamsesso.jsonlogic.evaluator.JsonLogicEvaluationException;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class StringCompTest {

    @Test
    public void startsWithEvaluation() throws JsonLogicEvaluationException {
        // given
        final StringComp startsWith = new StringComp(StringComp.Type.STARTS_WITH);

        // when
        Object result = startsWith.evaluate(Arrays.asList("abc@123.com", "abc"), new Object(), "jsonPath");

        // then
        if (!(result instanceof Boolean)) {
            fail("Result is not of type Boolean");
        }

        assertThat((Boolean) result).isTrue();
    }

    @Test
    public void endsWithEvaluation() throws JsonLogicEvaluationException {
        // given
        final StringComp endsWith = new StringComp(StringComp.Type.ENDS_WITH);

        // when
        Object result = endsWith.evaluate(Arrays.asList("abc@123.com", "123.com"), new Object(), "jsonPath");

        // then
        if (!(result instanceof Boolean)) {
            fail("Result is not of type Boolean");
        }

        assertThat((Boolean) result).isTrue();
    }

    @Test
    public void invalidTypeCheckArg1() throws JsonLogicEvaluationException {
        // given
        final StringComp operator = new StringComp(StringComp.Type.STARTS_WITH);

        // when
        Object result = operator.evaluate(Arrays.asList(1230, "12"), new Object(), "jsonPath");

        // then
        assertThat(result).isNull();
    }

    @Test
    public void invalidTypeCheckArg2() throws JsonLogicEvaluationException {
        // given
        final StringComp operator = new StringComp(StringComp.Type.STARTS_WITH);

        // when
        Object result = operator.evaluate(Arrays.asList("abc@123.com", 123), new Object(), "jsonPath");

        // then
        assertThat(result).isNull();
    }

    @Test
    public void invalidNumberOfArgs() throws JsonLogicEvaluationException {
        // given
        final StringComp operator = new StringComp(StringComp.Type.STARTS_WITH);

        // when - too many args
        Object result = operator.evaluate(Arrays.asList("123", "12", "1"), new Object(), "jsonPath");

        // then
        assertThat(result).isNull();
    }

    @Test
    public void tooFewArgs() throws JsonLogicEvaluationException {
        // given
        final StringComp startsWith = new StringComp(StringComp.Type.STARTS_WITH);
        final StringComp endsWith = new StringComp(StringComp.Type.ENDS_WITH);

        // when/then - single arg returns null
        assertThat(startsWith.evaluate(Arrays.asList("abc"), new Object(), "jsonPath"))
                .isNull();
        assertThat(endsWith.evaluate(Arrays.asList("xyz"), new Object(), "jsonPath"))
                .isNull();
    }

    @Test
    public void endsWithNonStringInput() throws JsonLogicEvaluationException {
        // given
        final StringComp operator = new StringComp(StringComp.Type.ENDS_WITH);

        // when - non-string first arg
        Object result = operator.evaluate(Arrays.asList(123, "abc"), new Object(), "jsonPath");

        // then
        assertThat(result).isNull();
    }
}
