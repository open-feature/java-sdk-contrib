package dev.openfeature.contrib.tools.providertck;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * One run of the conformance suite against one provider in one configuration.
 *
 * <p>The field names and nesting are fixed by the report schema in the OpenFeature specification
 * repository. This class is deliberately a transcription of that schema rather than a shape that
 * would be convenient in Java, because the point of the format is that every language's TCK emits
 * the same document.
 *
 * <p>Fields left {@code null} are omitted from the JSON. The schema sets
 * {@code additionalProperties: false} throughout, so an unexpected field is a validation failure
 * rather than something a consumer ignores.
 *
 * @see <a href="https://github.com/open-feature/spec/issues/424">open-feature/spec#424</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ConformanceReport {

    /**
     * The major version of the report schema this document conforms to.
     *
     * <p>An integer as a string, so a consumer can reject a report it does not understand rather
     * than guessing at it.
     */
    public static final String SCHEMA_VERSION = "1";

    /** The schema version this document claims. */
    public final String schemaVersion;

    /** What was tested. */
    public final Provider provider;

    /** Which OpenFeature SDK the provider was exercised through. */
    public final Sdk sdk;

    /** What asked the questions, and which questions. */
    public final Tck tck;

    /** What the provider was pointed at. */
    public final Backend backend;

    /** Per-capability outcome, keyed by Gherkin tag including the leading at-sign. */
    public final Map<String, CapabilityResult> capabilities;

    /** Per-scenario outcome, one entry per scenario in the suite. */
    public final List<ScenarioResult> scenarios;

    ConformanceReport(
            Provider provider,
            Sdk sdk,
            Tck tck,
            Backend backend,
            Map<String, CapabilityResult> capabilities,
            List<ScenarioResult> scenarios) {
        this.schemaVersion = SCHEMA_VERSION;
        this.provider = provider;
        this.sdk = sdk;
        this.tck = tck;
        this.backend = backend;
        this.capabilities = capabilities;
        this.scenarios = scenarios;
    }

    /** Identifies the provider under test. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class Provider {

        /**
         * What the provider calls itself, through its own metadata.
         *
         * <p>Not the name of the suite. A suite name is chosen to read well in a failure message —
         * {@code flagd-rpc} — which makes it the {@link #configuration}, not the identity.
         */
        public final String name;

        /** The language the provider is written in, always {@code java} here. */
        public final String language;

        /**
         * Which configuration of the provider was tested.
         *
         * <p>A provider with more than one materially different mode produces one report per mode,
         * and they are not interchangeable: flagd's RPC and in-process resolvers differ in whether
         * they emit {@code PROVIDER_STALE}, so a report keyed on the provider name alone would have
         * to pick one and misrepresent the other.
         */
        public final String configuration;

        Provider(String name, String language, String configuration) {
            this.name = name;
            this.language = language;
            this.configuration = configuration;
        }
    }

    /** Identifies the OpenFeature SDK the run went through. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class Sdk {

        /** Maven coordinates of the SDK, without a version. */
        public final String name;

        /** The resolved SDK version. */
        public final String version;

        Sdk(String name, String version) {
            this.name = name;
            this.version = version;
        }
    }

    /** Identifies the TCK implementation and the conformance artifacts it executed. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class Tck {

        /** Which TCK implementation produced this report. */
        public final String implementation;

        /** The version of that implementation. */
        public final String version;

        /** The open-feature/spec commit the executed artifacts came from. */
        public final String specRevision;

        /**
         * The git tree object ID of {@code specification/assets/provider-tck} at that revision.
         *
         * <p>Carried alongside the commit because it identifies the artifacts rather than the
         * commit: it is unchanged by unrelated edits elsewhere in the specification, and
         * {@code git rev-parse <specRevision>:specification/assets/provider-tck} must reproduce it,
         * so a revision recorded wrongly does not go unnoticed.
         */
        public final String assetsTree;

        Tck(String implementation, String version, String specRevision, String assetsTree) {
            this.implementation = implementation;
            this.version = version;
            this.specRevision = specRevision;
            this.assetsTree = assetsTree;
        }
    }

    /** Describes what the provider was pointed at. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class Backend {

        /** A short human-readable description of the stack under test. */
        public final String description;

        /**
         * How the backend was driven, {@code http} or {@code in-process}.
         *
         * <p>{@code in-process} is the narrow allowance made for providers with no backend; a report
         * claiming it for a provider that has one should be treated with suspicion.
         */
        public final String controlApi;

        Backend(String description, String controlApi) {
            this.description = description;
            this.controlApi = controlApi;
        }
    }

    /** The outcome of one capability, with the reason it is not simply {@code passed}. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class CapabilityResult {

        /** The capability's outcome across every scenario that gates on it. */
        public final Outcome state;

        /** Why, in a form someone reading a comparison page can use. */
        public final String reason;

        CapabilityResult(Outcome state, String reason) {
            this.state = state;
            this.reason = reason;
        }
    }

    /** The outcome of one scenario. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class ScenarioResult {

        /** The feature file the scenario came from, without extension. */
        public final String feature;

        /** The scenario name as written in the feature file. */
        public final String name;

        /**
         * The Examples row this entry came from, keyed by column header, or {@code null} for a
         * scenario that did not come from a Scenario Outline.
         *
         * <p>Part of the entry's identity rather than decoration. Every row of an outline shares one
         * name, so eleven rows of the type-mismatch matrix produce eleven entries with the same
         * feature and name; without the parameters a report cannot say which of them failed.
         *
         * <p>Values are the cell contents verbatim, as strings. Gherkin has no types, so
         * {@code "1"} stays the string {@code 1} — coercing it would make the report say something
         * the table did not.
         */
        public final Map<String, String> example;

        /** The scenario's Gherkin tags, including any inherited from the feature. */
        public final List<String> tags;

        /** What happened. */
        public final Outcome outcome;

        /** For a skip, why it was skipped; for a failure, what failed. */
        public final String reason;

        /** How long the scenario took. */
        public final double durationMs;

        ScenarioResult(
                String feature,
                String name,
                Map<String, String> example,
                List<String> tags,
                Outcome outcome,
                String reason,
                double duration) {
            this.feature = feature;
            this.name = name;
            this.example = example;
            this.tags = tags;
            this.outcome = outcome;
            this.reason = reason;
            this.durationMs = duration;
        }
    }
}
