package dev.openfeature.contrib.tools.tck.internal.steps;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.openfeature.contrib.tools.tck.BackendControl;
import dev.openfeature.contrib.tools.tck.InProcessControl;
import dev.openfeature.contrib.tools.tck.ProviderTckHarness;
import dev.openfeature.contrib.tools.tck.TckState;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.ImmutableMetadata;
import io.cucumber.datatable.DataTable;
import io.cucumber.datatable.DataTableTypeRegistry;
import io.cucumber.datatable.DataTableTypeRegistryTableConverter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The flag-metadata assertions, whose wording and columns are the specification's own.
 *
 * <p>Each case is written so the opposite implementation would fail it: a value that matches and one
 * that does not, metadata that is empty and metadata that is not.
 */
class FlagMetadataStepsTest {

    /** A harness that brings nothing up. */
    private static final class StubHarness implements ProviderTckHarness {

        private final InProcessControl control = BackendControl.inProcess();

        @Override
        public FeatureProvider createProvider() {
            return control.createProvider();
        }

        @Override
        public BackendControl backendControl() {
            return control;
        }
    }

    private static FlagSteps stepsFor(TckState state) {
        StubHarness harness = new StubHarness();
        return new FlagSteps(state, harness, harness.backendControl());
    }

    private static TckState stateWith(ImmutableMetadata metadata) {
        TckState state = new TckState();
        state.evaluation = FlagEvaluationDetails.<Boolean>builder()
                .flagKey("metadata-flag")
                .value(true)
                .flagMetadata(metadata)
                .build();
        return state;
    }

    /** A table carrying the specification's {@code key | metadata_type | value} header. */
    private static DataTable table(String... rows) {
        List<List<String>> cells = new ArrayList<>();
        cells.add(Arrays.asList("key", "metadata_type", "value"));
        for (String row : rows) {
            cells.add(Arrays.asList(row.split("\\s*\\|\\s*")));
        }
        return DataTable.create(
                cells, new DataTableTypeRegistryTableConverter(new DataTableTypeRegistry(Locale.ENGLISH)));
    }

    @Test
    @DisplayName("every type the specification's table uses is compared by that type")
    void typedEntriesAreCompared() {
        TckState state = stateWith(ImmutableMetadata.builder()
                .addString("string", "1.0.2")
                .addInteger("integer", 2)
                .addDouble("float", 0.1)
                .addBoolean("boolean", true)
                .build());

        assertThatCode(() -> stepsFor(state)
                        .theResolvedMetadataShouldContain(table(
                                "string | String | 1.0.2",
                                "integer | Integer | 2",
                                "float | Float | 0.1",
                                "boolean | Boolean | true")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a value that differs fails, naming the key and what the provider sent")
    void aWrongValueFails() {
        TckState state =
                stateWith(ImmutableMetadata.builder().addInteger("integer", 2).build());

        assertThatThrownBy(() -> stepsFor(state).theResolvedMetadataShouldContain(table("integer | Integer | 3")))
                .hasMessageContaining("metadata 'integer' (Integer)");
    }

    @Test
    @DisplayName("an entry the table does not list is not checked")
    void unlistedEntriesAreIgnored() {
        TckState state = stateWith(ImmutableMetadata.builder()
                .addString("listed", "yes")
                .addString("unlisted", "whatever")
                .build());

        assertThatCode(() -> stepsFor(state).theResolvedMetadataShouldContain(table("listed | String | yes")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("empty metadata satisfies the empty assertion, and anything else does not")
    void theEmptyAssertion() {
        assertThatCode(() ->
                        stepsFor(stateWith(ImmutableMetadata.builder().build())).theResolvedMetadataIsEmpty())
                .doesNotThrowAnyException();

        TckState withEntry =
                stateWith(ImmutableMetadata.builder().addString("a", "b").build());
        assertThatThrownBy(() -> stepsFor(withEntry).theResolvedMetadataIsEmpty())
                .hasMessageContaining("flag metadata");
    }

    @Test
    @DisplayName("a metadata type flag metadata cannot carry is refused, not silently compared")
    void anUnknownTypeIsRefused() {
        TckState state =
                stateWith(ImmutableMetadata.builder().addString("a", "b").build());

        assertThatThrownBy(() -> stepsFor(state).theResolvedMetadataShouldContain(table("a | Object | {}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Flag metadata is String, Boolean, Integer or Float");
    }

    @Test
    @DisplayName("asserting metadata before any evaluation says which step is missing")
    void noEvaluationIsAClearFailure() {
        TckState state = new TckState();

        assertThatThrownBy(() -> stepsFor(state).theResolvedMetadataIsEmpty())
                .hasMessageContaining("the flag was evaluated with details");
    }
}
