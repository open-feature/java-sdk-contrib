package dev.openfeature.contrib.providers.gofeatureflag.e2e;

import static dev.openfeature.contrib.providers.gofeatureflag.e2e.RelayProxyTestHelper.TARGETING_KEY;
import static dev.openfeature.contrib.providers.gofeatureflag.e2e.RelayProxyTestHelper.evaluate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import dev.openfeature.contrib.providers.gofeatureflag.TestUtils;
import dev.openfeature.contrib.providers.gofeatureflag.bean.EvaluationType;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.MutableContext;
import dev.openfeature.sdk.MutableStructure;
import dev.openfeature.sdk.Reason;
import dev.openfeature.sdk.Value;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Runs the provider against a real relay proxy serving the cross-language fixture of Appendix B.4,
 * go-feature-flag's openfeature/provider_tests, with its flags and relay proxy configurations vendored
 * unmodified.
 */
@Testcontainers
class FlagEvaluationIntegrationTest extends AbstractRelayProxyIntegrationTest {
    @Container
    private static final GenericContainer<?> relayProxy = RelayProxyTestHelper.relayProxy();

    private static final String TARGETING_MATCH = Reason.TARGETING_MATCH.name();
    private static final String DEFAULT = Reason.DEFAULT.name();
    private static final Map<String, Object> PR_916 = Map.of(
            "description", "this is a test",
            "pr_link", "https://github.com/thomaspoignant/go-feature-flag/pull/916");

    static Stream<Arguments> enabledFlags() {
        val trueObject = new Value(new MutableStructure()
                .add("test", "test1")
                .add("test2", false)
                .add("test3", 123.3)
                .add("test4", 1));
        val semverMetadata = Map.<String, Object>of(
                "description", "this is a semver matching test",
                "pr_link", "https://github.com/thomaspoignant/go-feature-flag/pull/4764");
        return Arrays.stream(EvaluationType.values())
                .flatMap(type -> Stream.of(
                        Arguments.of(type, "bool_targeting_match", false, true, "True", TARGETING_MATCH, PR_916),
                        Arguments.of(type, "string_key", "", "CC0000", "True", TARGETING_MATCH, PR_916),
                        Arguments.of(type, "string_key_with_version", "", "CC0000", "True", TARGETING_MATCH, PR_916),
                        Arguments.of(type, "integer_key", 0, 100, "True", TARGETING_MATCH, PR_916),
                        Arguments.of(type, "double_key", 0.0, 100.25, "True", TARGETING_MATCH, PR_916),
                        Arguments.of(type, "object_key", new Value(), trueObject, "True", TARGETING_MATCH, PR_916),
                        Arguments.of(
                                type,
                                "flag-use-evaluation-context-enrichment",
                                "",
                                "A",
                                "A",
                                TARGETING_MATCH,
                                Map.of()),
                        Arguments.of(
                                type,
                                "boolean_semver_targeting_match",
                                true,
                                false,
                                "False",
                                DEFAULT,
                                semverMetadata)));
    }

    static Stream<Arguments> disabledFlags() {
        return Arrays.stream(EvaluationType.values())
                .flatMap(type -> Stream.of(
                        Arguments.of(type, "disabled_bool", false),
                        Arguments.of(type, "disabled_string", "sdk-default"),
                        Arguments.of(type, "disabled_int", 0),
                        Arguments.of(type, "disabled_float", 0.0),
                        Arguments.of(type, "disabled_interface", new Value())));
    }

    static Stream<Arguments> semverVersions() {
        return Arrays.stream(EvaluationType.values())
                .flatMap(type -> Stream.of(
                        Arguments.of(type, "10.0.0-10", true, "True", TARGETING_MATCH),
                        Arguments.of(type, "10.0.0-2", false, "False", DEFAULT)));
    }

    @DisplayName("the canonical context should resolve every enabled flag of the fixture")
    @ParameterizedTest(name = "{0} evaluation of {1}")
    @MethodSource("enabledFlags")
    void theCanonicalContextShouldResolveEveryEnabledFlagOfTheFixture(
            EvaluationType type,
            String flagKey,
            Object defaultValue,
            Object expectedValue,
            String expectedVariant,
            String expectedReason,
            Map<String, Object> flagMetadata) {
        val client = client(type, relayProxy, null);
        val expectedMetadata = new HashMap<>(flagMetadata);
        if (type == EvaluationType.REMOTE) {
            expectedMetadata.put("gofeatureflag_cacheable", true);
        }

        val got = evaluate(client, flagKey, defaultValue);

        assertEquals(expectedValue, got.getValue());
        assertEquals(expectedVariant, got.getVariant());
        assertEquals(expectedReason, got.getReason());
        assertEquals(
                expectedMetadata,
                got.getFlagMetadata().asUnmodifiableMap(),
                "the flag metadata unmodified, with nothing added but what the relay proxy adds when it evaluates");
    }

    @DisplayName("a context the targeting query does not match should resolve through the default rule")
    @ParameterizedTest(name = "{0} evaluation")
    @EnumSource(EvaluationType.class)
    void aContextTheTargetingQueryDoesNotMatchShouldResolveThroughTheDefaultRule(EvaluationType type) {
        val client = client(type, relayProxy, null);
        val ctx = new MutableContext(TARGETING_KEY).add("email", "jane.doe@gofeatureflag.org");

        val got = client.getBooleanDetails("bool_targeting_match", false, ctx);

        assertEquals(true, got.getValue(), "the default rule sends 100% of the traffic to True");
        assertEquals("True", got.getVariant());
        assertEquals(DEFAULT, got.getReason());
    }

    @DisplayName("a semver targeting query should compare prerelease versions numerically")
    @ParameterizedTest(name = "{0} evaluation of version {1}")
    @MethodSource("semverVersions")
    void aSemverTargetingQueryShouldComparePrereleaseVersionsNumerically(
            EvaluationType type, String version, boolean expectedValue, String expectedVariant, String reason) {
        val client = client(type, relayProxy, null);
        val ctx = new MutableContext(TARGETING_KEY).add("version", version);

        val got = client.getBooleanDetails("boolean_semver_targeting_match", false, ctx);

        assertEquals(expectedValue, got.getValue());
        assertEquals(expectedVariant, got.getVariant());
        assertEquals(reason, got.getReason());
    }

    @DisplayName("an unknown flag should resolve to the default value with FLAG_NOT_FOUND")
    @ParameterizedTest(name = "{0} evaluation")
    @EnumSource(EvaluationType.class)
    void anUnknownFlagShouldResolveToTheDefaultValueWithFlagNotFound(EvaluationType type) {
        val client = client(type, relayProxy, null);

        val got = client.getBooleanDetails("does_not_exist", false, TestUtils.defaultEvaluationContext);

        assertEquals(false, got.getValue());
        assertEquals(ErrorCode.FLAG_NOT_FOUND, got.getErrorCode());
        assertEquals(Reason.ERROR.name(), got.getReason());
    }

    @DisplayName("a flag evaluated as the wrong type should resolve to the default value with TYPE_MISMATCH")
    @ParameterizedTest(name = "{0} evaluation")
    @EnumSource(EvaluationType.class)
    void aFlagEvaluatedAsTheWrongTypeShouldResolveToTheDefaultValueWithTypeMismatch(EvaluationType type) {
        val client = client(type, relayProxy, null);

        val got = client.getStringDetails("bool_targeting_match", "sdk-default", TestUtils.defaultEvaluationContext);

        assertEquals("sdk-default", got.getValue());
        assertEquals(ErrorCode.TYPE_MISMATCH, got.getErrorCode());
        assertEquals(Reason.ERROR.name(), got.getReason());
    }

    @DisplayName("a disabled flag should resolve to the default value with DISABLED")
    @ParameterizedTest(name = "{0} evaluation of {1}")
    @MethodSource("disabledFlags")
    void aDisabledFlagShouldResolveToTheDefaultValueWithDisabled(
            EvaluationType type, String flagKey, Object defaultValue) {
        assumeFalse(
                type == EvaluationType.REMOTE,
                "the relay proxy answers a disabled flag with a null value over OFREP, which the OFREP"
                        + " resolver reports as FLAG_NOT_FOUND");
        val client = client(type, relayProxy, null);

        val got = evaluate(client, flagKey, defaultValue);

        assertEquals(defaultValue, got.getValue());
        assertEquals(Reason.DISABLED.name(), got.getReason());
        assertNull(got.getErrorCode());
    }
}
