package dev.openfeature.contrib.hooks.otel;

import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.FlagValueType;
import dev.openfeature.sdk.HookContext;
import dev.openfeature.sdk.MutableContext;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static dev.openfeature.contrib.hooks.otel.OtelCommons.ERROR_KEY;
import static dev.openfeature.contrib.hooks.otel.OtelCommons.REASON_KEY;
import static dev.openfeature.contrib.hooks.otel.OtelCommons.flagKeyAttributeKey;
import static dev.openfeature.contrib.hooks.otel.OtelCommons.providerNameAttributeKey;
import static dev.openfeature.contrib.hooks.otel.OtelCommons.variantAttributeKey;
import static org.assertj.core.api.Assertions.assertThat;

class OpenTelemetryMetricHookTest {
    private final HookContext<String> commonHookContext = HookContext.<String>builder()
            .flagKey("key")
            .type(FlagValueType.STRING)
            .providerMetadata(() -> "UnitTest")
            .ctx(new MutableContext())
            .defaultValue("value")
            .build();

    private OpenTelemetryExtension telemetryExtension;

    @BeforeEach
    public void setup() {
        telemetryExtension = OpenTelemetryExtension.create();
    }


    @Test
    public void before_stage_validation() {
        // given
        final OpenTelemetryMetricHook metricHook =
                new OpenTelemetryMetricHook(telemetryExtension.getOpenTelemetry());

        final List<String> metricNames =
                Arrays.asList("feature_flag.evaluation_active_count", "feature_flag.evaluation_requests_total");

        // when
        metricHook.before(commonHookContext, null);
        List<MetricData> metrics = telemetryExtension.getMetrics();

        // then
        assertThat(metrics).hasSize(2);
        metrics.forEach(m -> assertThat(m.getName()).isIn(metricNames));
    }

    @Test
    public void after_stage_validation() {
        // given
        final OpenTelemetryMetricHook metricHook =
                new OpenTelemetryMetricHook(telemetryExtension.getOpenTelemetry());

        final FlagEvaluationDetails<String> evaluationDetails = FlagEvaluationDetails.<String>builder()
                .flagKey("key")
                .value("value")
                .variant("variant")
                .reason("STATIC")
                .build();

        // when
        metricHook.after(commonHookContext, evaluationDetails, null);
        List<MetricData> metrics = telemetryExtension.getMetrics();

        // then
        assertThat(metrics).hasSize(1);

        final MetricData successMetric = metrics.get(0);
        assertThat(successMetric.getName()).isEqualTo("feature_flag.evaluation_success_total");

        // Validate dimensions(attributes) setup by extracting the first point
        Optional<LongPointData> data = successMetric.getLongSumData().getPoints().stream().findFirst();
        assertThat(data.isPresent()).isTrue();

        final LongPointData longPointData = data.get();
        final Attributes attributes = longPointData.getAttributes();

        assertThat(attributes.get(flagKeyAttributeKey)).isEqualTo("key");
        assertThat(attributes.get(providerNameAttributeKey)).isEqualTo("UnitTest");
        assertThat(attributes.get(variantAttributeKey)).isEqualTo("variant");
        assertThat(attributes.get(AttributeKey.stringKey(REASON_KEY))).isEqualTo("STATIC");
    }

    @Test
    public void error_stage_validation() {
        // given
        final OpenTelemetryMetricHook metricHook =
                new OpenTelemetryMetricHook(telemetryExtension.getOpenTelemetry());

        final Exception exception = new Exception("some_exception");

        // when
        metricHook.error(commonHookContext, exception, null);
        List<MetricData> metrics = telemetryExtension.getMetrics();

        // then
        assertThat(metrics).hasSize(1);

        final MetricData successMetric = metrics.get(0);
        assertThat(successMetric.getName()).isEqualTo("feature_flag.evaluation_error_total");

        // Validate dimensions(attributes) setup by extracting the first point
        Optional<LongPointData> data = successMetric.getLongSumData().getPoints().stream().findFirst();
        assertThat(data.isPresent()).isTrue();

        final LongPointData longPointData = data.get();
        final Attributes attributes = longPointData.getAttributes();

        assertThat(attributes.get(flagKeyAttributeKey)).isEqualTo("key");
        assertThat(attributes.get(providerNameAttributeKey)).isEqualTo("UnitTest");
        assertThat(attributes.get(AttributeKey.stringKey(ERROR_KEY))).isEqualTo("some_exception");
    }


    @Test
    public void finally_stage_validation() {
        // given
        final OpenTelemetryMetricHook metricHook =
                new OpenTelemetryMetricHook(telemetryExtension.getOpenTelemetry());


        // when
        metricHook.finallyAfter(commonHookContext, null);
        List<MetricData> metrics = telemetryExtension.getMetrics();

        // then
        assertThat(metrics).hasSize(1);

        final MetricData successMetric = metrics.get(0);
        assertThat(successMetric.getName()).isEqualTo("feature_flag.evaluation_active_count");

        // Validate dimensions(attributes) setup by extracting the first point
        Optional<LongPointData> data = successMetric.getLongSumData().getPoints().stream().findFirst();
        assertThat(data.isPresent()).isTrue();

        final LongPointData longPointData = data.get();
        final Attributes attributes = longPointData.getAttributes();

        assertThat(attributes.get(flagKeyAttributeKey)).isEqualTo("key");
    }
}