package dev.openfeature.contrib.hooks.otel;

import static dev.openfeature.contrib.hooks.otel.OTelCommons.REASON_KEY;
import static dev.openfeature.contrib.hooks.otel.OTelCommons.flagKeyAttributeKey;
import static dev.openfeature.contrib.hooks.otel.OTelCommons.providerNameAttributeKey;
import static dev.openfeature.contrib.hooks.otel.OTelCommons.variantAttributeKey;
import static org.assertj.core.api.Assertions.assertThat;

import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.FlagValueType;
import dev.openfeature.sdk.HookContext;
import dev.openfeature.sdk.ImmutableMetadata;
import dev.openfeature.sdk.MutableContext;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MetricsHookTest {
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
        final MetricsHook metricHook = new MetricsHook(telemetryExtension.getOpenTelemetry());

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
        final MetricsHook metricHook = new MetricsHook(telemetryExtension.getOpenTelemetry());

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
        Optional<LongPointData> data =
                successMetric.getLongSumData().getPoints().stream().findFirst();
        assertThat(data.isPresent()).isTrue();

        final LongPointData longPointData = data.get();
        final Attributes attributes = longPointData.getAttributes();

        assertThat(attributes.get(flagKeyAttributeKey)).isEqualTo("key");
        assertThat(attributes.get(providerNameAttributeKey)).isEqualTo("UnitTest");
        assertThat(attributes.get(variantAttributeKey)).isEqualTo("variant");
        assertThat(attributes.get(AttributeKey.stringKey(REASON_KEY))).isEqualTo("STATIC");
    }

    @Test
    public void after_stage_validation_of_custom_dimensions() {
        // given
        final List<DimensionDescription> dimensionList = new ArrayList<>();
        dimensionList.add(new DimensionDescription("boolean", Boolean.class));
        dimensionList.add(new DimensionDescription("integer", Integer.class));
        dimensionList.add(new DimensionDescription("long", Long.class));
        dimensionList.add(new DimensionDescription("float", Float.class));
        dimensionList.add(new DimensionDescription("double", Double.class));
        dimensionList.add(new DimensionDescription("string", String.class));

        final MetricsHook metricHook = new MetricsHook(
                telemetryExtension.getOpenTelemetry(),
                MetricHookOptions.builder().setDimensions(dimensionList).build());

        final ImmutableMetadata metadata = ImmutableMetadata.builder()
                .addBoolean("boolean", true)
                .addInteger("integer", 1)
                .addLong("long", 1L)
                .addFloat("float", 1.0F)
                .addDouble("double", 1.0D)
                .addString("string", "string")
                .build();

        final FlagEvaluationDetails<String> evaluationDetails = FlagEvaluationDetails.<String>builder()
                .flagKey("key")
                .value("value")
                .variant("variant")
                .reason("STATIC")
                .flagMetadata(metadata)
                .build();

        // when
        metricHook.after(commonHookContext, evaluationDetails, null);
        List<MetricData> metrics = telemetryExtension.getMetrics();

        // then
        assertThat(metrics).hasSize(1);

        final MetricData metricData = metrics.get(0);
        final Optional<LongPointData> pointData =
                metricData.getLongSumData().getPoints().stream().findFirst();
        assertThat(pointData).isPresent();

        final LongPointData longPointData = pointData.get();
        final Attributes attributes = longPointData.getAttributes();

        assertThat(attributes.get(AttributeKey.stringKey("string"))).isEqualTo("string");
        assertThat(attributes.get(AttributeKey.doubleKey("double"))).isEqualTo(1.0D);
        assertThat(attributes.get(AttributeKey.doubleKey("float"))).isEqualTo(1.0F);
        assertThat(attributes.get(AttributeKey.longKey("long"))).isEqualTo(1L);
        assertThat(attributes.get(AttributeKey.longKey("integer"))).isEqualTo(1);
        assertThat(attributes.get(AttributeKey.booleanKey("boolean"))).isEqualTo(true);
    }

    @Test
    public void error_stage_validation() {
        // given
        final MetricsHook metricHook = new MetricsHook(
                telemetryExtension.getOpenTelemetry(),
                MetricHookOptions.builder()
                        .extraAttributes(
                                Attributes.builder().put("scope", "app-a").build())
                        .build());

        final Exception exception = new Exception("some_exception");

        // when
        metricHook.error(commonHookContext, exception, null);
        List<MetricData> metrics = telemetryExtension.getMetrics();

        // then
        assertThat(metrics).hasSize(1);

        final MetricData successMetric = metrics.get(0);
        assertThat(successMetric.getName()).isEqualTo("feature_flag.evaluation_error_total");

        // Validate dimensions(attributes) setup by extracting the first point
        Optional<LongPointData> data =
                successMetric.getLongSumData().getPoints().stream().findFirst();
        assertThat(data.isPresent()).isTrue();

        final LongPointData longPointData = data.get();
        final Attributes attributes = longPointData.getAttributes();

        assertThat(attributes.get(flagKeyAttributeKey)).isEqualTo("key");
        assertThat(attributes.get(providerNameAttributeKey)).isEqualTo("UnitTest");
        assertThat(attributes.get(AttributeKey.stringKey("scope"))).isEqualTo("app-a");
    }

    @Test
    public void finally_stage_validation() {
        // given
        final MetricsHook metricHook = new MetricsHook(telemetryExtension.getOpenTelemetry());

        // when
        metricHook.finallyAfter(commonHookContext, null);
        List<MetricData> metrics = telemetryExtension.getMetrics();

        // then
        assertThat(metrics).hasSize(1);

        final MetricData successMetric = metrics.get(0);
        assertThat(successMetric.getName()).isEqualTo("feature_flag.evaluation_active_count");

        // Validate dimensions(attributes) setup by extracting the first point
        Optional<LongPointData> data =
                successMetric.getLongSumData().getPoints().stream().findFirst();
        assertThat(data.isPresent()).isTrue();

        final LongPointData longPointData = data.get();
        final Attributes attributes = longPointData.getAttributes();

        assertThat(attributes.get(flagKeyAttributeKey)).isEqualTo("key");
    }

    @Test
    public void hook_option_validation() {
        // given
        MetricHookOptions hookOptions = MetricHookOptions.builder()
                .attributeSetter(metadata -> Attributes.builder()
                        .put("boolean", metadata.getBoolean("boolean"))
                        .put("integer", metadata.getInteger("integer"))
                        .put("long", metadata.getLong("long"))
                        .put("float", metadata.getFloat("float"))
                        .put("double", metadata.getDouble("double"))
                        .put("string", metadata.getString("string"))
                        .build())
                .extraAttributes(Attributes.builder().put("scope", "value").build())
                .build();

        final MetricsHook metricHook = new MetricsHook(telemetryExtension.getOpenTelemetry(), hookOptions);

        final ImmutableMetadata metadata = ImmutableMetadata.builder()
                .addBoolean("boolean", true)
                .addInteger("integer", 1)
                .addLong("long", 1L)
                .addFloat("float", 1.0F)
                .addDouble("double", 1.0D)
                .addString("string", "string")
                .build();

        final FlagEvaluationDetails<String> evaluationDetails = FlagEvaluationDetails.<String>builder()
                .flagKey("key")
                .value("value")
                .variant("variant")
                .reason("STATIC")
                .flagMetadata(metadata)
                .build();

        // when
        metricHook.after(commonHookContext, evaluationDetails, null);
        List<MetricData> metrics = telemetryExtension.getMetrics();

        // then
        assertThat(metrics).hasSize(1);

        final MetricData metricData = metrics.get(0);
        final Optional<LongPointData> pointData =
                metricData.getLongSumData().getPoints().stream().findFirst();
        assertThat(pointData).isPresent();

        final LongPointData longPointData = pointData.get();
        final Attributes attributes = longPointData.getAttributes();

        assertThat(attributes.get(AttributeKey.stringKey("string"))).isEqualTo("string");
        assertThat(attributes.get(AttributeKey.doubleKey("double"))).isEqualTo(1.0D);
        assertThat(attributes.get(AttributeKey.doubleKey("float"))).isEqualTo(1.0F);
        assertThat(attributes.get(AttributeKey.longKey("long"))).isEqualTo(1L);
        assertThat(attributes.get(AttributeKey.longKey("integer"))).isEqualTo(1);
        assertThat(attributes.get(AttributeKey.booleanKey("boolean"))).isEqualTo(true);
        assertThat(attributes.get(AttributeKey.stringKey("scope"))).isEqualTo("value");
    }
}
