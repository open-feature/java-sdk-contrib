package dev.openfeature.contrib.tools.providertck;

import io.cucumber.gherkin.GherkinParser;
import io.cucumber.messages.types.Envelope;
import io.cucumber.messages.types.Examples;
import io.cucumber.messages.types.Feature;
import io.cucumber.messages.types.FeatureChild;
import io.cucumber.messages.types.GherkinDocument;
import io.cucumber.messages.types.Scenario;
import io.cucumber.messages.types.TableCell;
import io.cucumber.messages.types.TableRow;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves which Examples row a scenario came from, so the report can tell one row from another.
 *
 * <p>Every row of a Scenario Outline shares one scenario name. The type-mismatch matrix in
 * {@code errors.feature} is eleven rows, so a report identified by feature and name alone carries
 * eleven entries that nothing distinguishes: if one row fails and ten pass, the report cannot say
 * which failed, and a consumer keying on feature and name keeps whichever it saw last. The
 * parameters are the identity, so the report records them.
 *
 * <p><strong>How the row is found.</strong> Cucumber's {@code TestCaseFinished} carries a
 * {@link io.cucumber.plugin.event.TestCase}, and a test case is a compiled pickle that no longer
 * knows it came from a table. What it does carry is a URI and a location, and for an outline-derived
 * pickle that location is the Examples row: {@code TestCase.getLocation()} resolves the <em>last</em>
 * of the pickle's AST node ids, which the pickle compiler sets to the {@code TableRow}. A plain
 * scenario's last AST node is the scenario itself. So a line number in a feature file is enough to
 * tell the two apart, provided the feature file is parsed as well as executed.
 *
 * <p>The source comes from Cucumber's own {@code TestSourceRead} event rather than from a file or a
 * classpath resource. Cucumber has already located and decoded the feature — resolving
 * {@code classpath:features/errors.feature} a second time would mean reimplementing that lookup,
 * with a different answer whenever a consumer supplies features from somewhere else. The event is
 * published before any scenario in that feature runs, so the table is always in place by the time a
 * result needs it.
 *
 * <p>Parsing uses the Gherkin parser Cucumber already depends on, so the report reads the same
 * document the runner executed rather than a second interpretation of the syntax.
 */
final class ScenarioExamples {

    private static final Logger log = LoggerFactory.getLogger(ScenarioExamples.class);

    /**
     * Examples rows by feature URI, then by the line the row sits on.
     *
     * <p>Concurrent because {@link io.cucumber.plugin.ConcurrentEventListener} permits events from
     * several threads. The TCK suite pins Cucumber to serial execution, but this class is not the
     * place to depend on that.
     */
    private final Map<URI, Map<Integer, Map<String, String>>> rowsByUri = new ConcurrentHashMap<>();

    /**
     * Parses a feature file and remembers every Examples row in it.
     *
     * @param uri the feature's URI, as Cucumber reports it
     * @param source the feature file's text
     */
    void read(URI uri, String source) {
        if (uri == null || source == null) {
            return;
        }

        Map<Integer, Map<String, String>> rows = new LinkedHashMap<>();
        try (Stream<Envelope> envelopes = GherkinParser.builder()
                .includeSource(false)
                .includeGherkinDocument(true)
                .includePickles(false)
                .build()
                .parse(uri.toString(), source.getBytes(StandardCharsets.UTF_8))) {
            envelopes.forEach(envelope -> envelope.getGherkinDocument()
                    .flatMap(GherkinDocument::getFeature)
                    .ifPresent(feature -> collect(feature, rows)));
        } catch (RuntimeException e) {
            // Cucumber parsed this same source to produce the scenarios, so failing here means the
            // two parsers disagree rather than that the feature is broken. Report the entries
            // without their parameters rather than failing a run over the report's own metadata:
            // the outcomes are still correct, and the warning says why the entries are ambiguous.
            log.warn(
                    "provider-tck: could not parse {} for its Examples tables, so scenarios from that "
                            + "feature will be reported without the row they came from",
                    uri,
                    e);
            return;
        }

        rowsByUri.put(uri, Collections.unmodifiableMap(rows));
    }

    /**
     * Returns the Examples row a scenario came from.
     *
     * @param uri the feature's URI, as Cucumber reports it
     * @param line the line the test case reported as its location
     * @return the row's parameters keyed by column header, or {@code null} for a scenario that did
     *     not come from a Scenario Outline
     */
    Map<String, String> rowAt(URI uri, Integer line) {
        if (uri == null || line == null) {
            return null;
        }
        return rowsByUri.getOrDefault(uri, Collections.emptyMap()).get(line);
    }

    private static void collect(Feature feature, Map<Integer, Map<String, String>> rows) {
        for (FeatureChild child : feature.getChildren()) {
            child.getScenario().ifPresent(scenario -> collect(scenario, rows));
            child.getRule().ifPresent(rule -> rule.getChildren()
                    .forEach(ruleChild -> ruleChild.getScenario().ifPresent(scenario -> collect(scenario, rows))));
        }
    }

    private static void collect(Scenario scenario, Map<Integer, Map<String, String>> rows) {
        for (Examples examples : scenario.getExamples()) {
            List<String> headers =
                    examples.getTableHeader().map(ScenarioExamples::valuesOf).orElse(Collections.emptyList());
            if (headers.isEmpty()) {
                continue;
            }
            for (TableRow row : examples.getTableBody()) {
                Map<String, String> parameters = parametersOf(headers, valuesOf(row));
                if (!parameters.isEmpty()) {
                    rows.put(row.getLocation().getLine().intValue(), Collections.unmodifiableMap(parameters));
                }
            }
        }
    }

    /**
     * Pairs a row's cells with the column headers, verbatim.
     *
     * <p>No coercion and no trimming beyond the parser's own: Gherkin has no types, so {@code "1"}
     * is the string {@code 1} and turning it into a number would make the report say something the
     * table did not. Keys are in column order, which is how the table reads.
     *
     * <p>A row with a different number of cells than the table has headers cannot occur — Gherkin
     * rejects such a table before Cucumber compiles a pickle from it — but pairing only as far as
     * the shorter of the two keeps a malformed document from throwing out of a reporting path.
     */
    private static Map<String, String> parametersOf(List<String> headers, List<String> cells) {
        Map<String, String> parameters = new LinkedHashMap<>();
        int paired = Math.min(headers.size(), cells.size());
        for (int i = 0; i < paired; i++) {
            parameters.put(headers.get(i), cells.get(i));
        }
        return parameters;
    }

    private static List<String> valuesOf(TableRow row) {
        List<String> values = new ArrayList<>(row.getCells().size());
        for (TableCell cell : row.getCells()) {
            values.add(cell.getValue());
        }
        return values;
    }
}
