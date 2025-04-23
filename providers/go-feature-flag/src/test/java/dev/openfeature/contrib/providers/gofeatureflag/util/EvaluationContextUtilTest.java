package dev.openfeature.contrib.providers.gofeatureflag.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openfeature.sdk.MutableContext;
import org.junit.jupiter.api.Test;

class EvaluationContextUtilTest {

    @Test
    void testIsAnonymousUser_WhenContextIsNull_ShouldReturnTrue() {
        assertTrue(
                EvaluationContextUtil.isAnonymousUser(null),
                "Expected true when context is null");
    }

    @Test
    void testIsAnonymousUser_WhenAnonymousFieldIsTrue_ShouldReturnTrue() {
        MutableContext ctx = new MutableContext();
        ctx.add("anonymous", true);
        assertTrue(
                EvaluationContextUtil.isAnonymousUser(ctx)
                , "Expected true when anonymous field is true");
    }

    @Test
    void testIsAnonymousUser_WhenAnonymousFieldIsFalse_ShouldReturnFalse() {
        MutableContext ctx = new MutableContext();
        ctx.add("anonymous", false);
        assertFalse(
                EvaluationContextUtil.isAnonymousUser(ctx),
                "Expected false when anonymous field is false");
    }

    @Test
    void testIsAnonymousUser_WhenAnonymousFieldIsMissing_ShouldReturnFalse() {
        MutableContext ctx = new MutableContext();
        assertFalse(
                EvaluationContextUtil.isAnonymousUser(ctx)
                , "Expected false when anonymous field is missing");
    }
}
