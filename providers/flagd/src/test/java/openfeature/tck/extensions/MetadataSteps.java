package openfeature.tck.extensions;

import static org.assertj.core.api.Assertions.assertThat;

import dev.openfeature.contrib.tools.tck.TckState;
import dev.openfeature.sdk.ImmutableMetadata;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Then;
import java.io.IOException;
import java.util.Map;

/**
 * Assertions on the flag metadata of the last evaluation, for the scenarios ported from the
 * flagd-testbed's {@code metadata.feature}.
 */
public class MetadataSteps {

    private final TckState state;

    public MetadataSteps(TckState state) {
        this.state = state;
    }

    /**
     * Asserts typed metadata entries on the last evaluation. Entries not listed are not checked.
     *
     * @param table rows with {@code key}, {@code metadata_type} and {@code value} columns
     */
    @Then("the resolved metadata should contain")
    public void theResolvedMetadataShouldContain(DataTable table) throws IOException {
        ImmutableMetadata metadata = requireMetadata();
        for (Map<String, String> row : table.asMaps(String.class, String.class)) {
            String key = row.get("key");
            String type = row.get("metadata_type");
            Object expected = FlagdSteps.convert(row.get("value"), type);
            Object actual;
            switch (type) {
                case "String":
                    actual = metadata.getString(key);
                    break;
                case "Boolean":
                    actual = metadata.getBoolean(key);
                    break;
                case "Integer":
                    actual = metadata.getInteger(key);
                    break;
                case "Float":
                    actual = metadata.getDouble(key);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown metadata type '" + type + "'");
            }
            assertThat(actual)
                    .as("metadata '%s' (%s) in %s", key, type, metadata.asUnmodifiableMap())
                    .isEqualTo(expected);
        }
    }

    /** Asserts that the last evaluation carries no flag metadata. */
    @Then("the resolved metadata is empty")
    public void theResolvedMetadataIsEmpty() {
        ImmutableMetadata metadata = requireMetadata();
        assertThat(metadata.asUnmodifiableMap()).as("flag metadata").isEmpty();
    }

    private ImmutableMetadata requireMetadata() {
        assertThat(state.evaluation)
                .as("an evaluation was recorded; did the scenario forget 'When the flag was evaluated with details'?")
                .isNotNull();
        ImmutableMetadata metadata = state.evaluation.getFlagMetadata();
        assertThat(metadata).as("flag metadata").isNotNull();
        return metadata;
    }
}
