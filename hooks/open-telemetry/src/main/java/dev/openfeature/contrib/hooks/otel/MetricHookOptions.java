package dev.openfeature.contrib.hooks.otel;

import dev.openfeature.sdk.ImmutableMetadata;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * OpenTelemetry hook options.
 */
@Builder
@Getter
@SuppressFBWarnings(value = {"EI_EXPOSE_REP"}, justification = "Dimension list can be mutable")

public class MetricHookOptions {

    /**
     * Custom handler to derive {@link Attributes} from flag evaluation metadata represented with
     * {@link ImmutableMetadata}.
     */
    private Function<ImmutableMetadata, Attributes> attributeSetter;

    /**
     * List of {@link DimensionDescription} to be extracted from flag evaluation metadata.
     */
    @Builder.Default
    private final List<DimensionDescription> setDimensions = Collections.emptyList();

    /**
     * Extra attributes added at hook constructor time.
     */
    @Builder.Default
    private Attributes extraAttributes = Attributes.empty();
}
