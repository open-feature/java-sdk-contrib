package dev.openfeature.contrib.tools.tck.internal.steps;

import static org.assertj.core.api.Assertions.assertThat;

import dev.openfeature.contrib.tools.tck.BackendControl;
import dev.openfeature.contrib.tools.tck.InProcessControl;
import dev.openfeature.contrib.tools.tck.ProviderTckHarness;
import dev.openfeature.contrib.tools.tck.TckState;
import dev.openfeature.sdk.FeatureProvider;
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
 * What a context table does to the evaluation context.
 *
 * <p>The targeting key is the part worth pinning: the step rebuilds the context rather than adding
 * to it, and rebuilding is where an earlier statement can be lost or a later one overridden.
 */
class ContextStepsTest {

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

    private static ContextSteps stepsFor(TckState state) {
        StubHarness harness = new StubHarness();
        return new ContextSteps(state, harness, harness.backendControl());
    }

    /** A table carrying the {@code key | type | value} header the step reads. */
    private static DataTable table(String... rows) {
        List<List<String>> cells = new ArrayList<>();
        cells.add(Arrays.asList("key", "type", "value"));
        for (String row : rows) {
            cells.add(Arrays.asList(row.split("\\s*\\|\\s*")));
        }
        // Cucumber's own converter: DataTable.create(cells) alone cannot answer asMaps, and the
        // step reads the table the way a scenario hands it over.
        return DataTable.create(
                cells, new DataTableTypeRegistryTableConverter(new DataTableTypeRegistry(Locale.ENGLISH)));
    }

    @Test
    @DisplayName("a targeting key set earlier survives a table that does not mention it")
    void anEarlierTargetingKeySurvives() {
        TckState state = new TckState();
        ContextSteps steps = stepsFor(state);
        steps.contextContainingTargetingKey("tim");

        steps.contextWithTheFollowingKeys(table("region | String | eu"));

        assertThat(state.context.getTargetingKey())
                .as("the table said nothing about it, so it is still the targeting key")
                .isEqualTo("tim");
        assertThat(state.context.getValue("region").asString()).isEqualTo("eu");
    }

    @Test
    @DisplayName("a table row may set the targeting key")
    void theTableMaySetTheTargetingKey() {
        TckState state = new TckState();
        ContextSteps steps = stepsFor(state);
        steps.contextContainingTargetingKey("tim");

        steps.contextWithTheFollowingKeys(table("targetingKey | String | ada"));

        assertThat(state.context.getTargetingKey())
                .as("the row is the later statement about it, so it is the one that holds")
                .isEqualTo("ada");
    }

    @Test
    @DisplayName("a row's type decides what the context carries")
    void theTableSetsTypedEntries() {
        TckState state = new TckState();
        ContextSteps steps = stepsFor(state);

        steps.contextWithTheFollowingKeys(
                table("region | String | eu", "editions | StringList | one, two", "fresh | Boolean | true"));

        assertThat(state.context.getValue("region").asString()).isEqualTo("eu");
        assertThat(state.context.getValue("editions").asList())
                .as("a StringList is a list, not one comma-joined string")
                .hasSize(2);
        assertThat(state.context.getValue("fresh").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("entries from two tables add up")
    void twoTablesAddUp() {
        TckState state = new TckState();
        ContextSteps steps = stepsFor(state);

        steps.contextWithTheFollowingKeys(table("region | String | eu"));
        steps.contextWithTheFollowingKeys(table("tier | String | gold"));

        assertThat(state.context.getValue("region").asString()).isEqualTo("eu");
        assertThat(state.context.getValue("tier").asString()).isEqualTo("gold");
    }
}
