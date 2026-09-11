package dev.openfeature.contrib.tools.providertck;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * The envelope of one conformance run: what was tested, what the provider claims, and where the
 * results are.
 *
 * <p>This document deliberately carries <strong>no</strong> per-scenario outcomes. The results
 * themselves are a standard format — a Cucumber Messages stream, referenced by {@link Results} —
 * because per-scenario outcomes, tags, Scenario Outline row identity and the executed feature source
 * are all already specified there. Defining them a second time here would create a format to
 * maintain and version and two places for the same fact to disagree.
 *
 * <p>The field names and nesting are fixed by the report schema in the OpenFeature specification
 * repository. This class is a transcription of that schema rather than a shape that would be
 * convenient in Java, because the point of the format is that every language's TCK emits the same
 * document.
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

    /** The capability set this provider claims. */
    public final Declaration declaration;

    /** Where the executed results live, and in what format. */
    public final Results results;

    /** Deviations the provider acknowledges, or {@code null} to say nothing. */
    public final List<KnownDeviation> knownDeviations;

    ConformanceReport(
            Provider provider,
            Sdk sdk,
            Tck tck,
            Backend backend,
            Declaration declaration,
            Results results,
            List<KnownDeviation> knownDeviations) {
        this.schemaVersion = SCHEMA_VERSION;
        this.provider = provider;
        this.sdk = sdk;
        this.tck = tck;
        this.backend = backend;
        this.declaration = declaration;
        this.results = results;
        this.knownDeviations = knownDeviations;
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

        /**
         * The open-feature/spec commit the executed artifacts came from.
         *
         * <p>The executed Gherkin no longer has to be taken on trust: the results stream carries the
         * {@code source} of every feature it ran, so a consumer can diff what executed against what
         * this revision contains. The revision still identifies the two artifacts the stream does
         * <em>not</em> carry — the canonical flag set and the control API definition.
         */
        public final String specRevision;

        Tck(String implementation, String version, String specRevision) {
            this.implementation = implementation;
            this.version = version;
            this.specRevision = specRevision;
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

    /** The capability set this provider claims, as the Gherkin tags that gate them. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class Declaration {

        /**
         * Capabilities the provider declares, including the leading at-sign.
         *
         * <p>An <strong>input</strong> to reading the results rather than a summary of them, which is
         * why it cannot be derived from the results payload and has to be stated here. A skipped
         * scenario in the stream says the question was not put to this provider; only the declaration
         * says whether that is because the provider declines the capability. Given the declaration
         * and a scenario's tags — both of which the stream carries — the reason for a skip follows
         * without being transported per scenario.
         */
        public final List<String> declared;

        Declaration(List<String> declared) {
            this.declared = declared;
        }
    }

    /** Where the executed results live, and in what format. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class Results {

        /** The ndjson protocol at {@code https://github.com/cucumber/messages}. */
        public static final String CUCUMBER_MESSAGES = "cucumber-messages";

        /** The results format, always {@link #CUCUMBER_MESSAGES} here. */
        public final String format;

        /**
         * The Cucumber Messages release the stream was produced against.
         *
         * <p>Messages is versioned and the four TCK implementations pin different releases -- this
         * one takes whatever cucumber-jvm bundles, while the Go TCK builds against v21 and the
         * Python one against 34.2.0 -- so a consumer holding two reports cannot assume one schema
         * validates both.
         *
         * <p>Guessing is worse than not validating. A later schema accepts messages this producer
         * could not have emitted, and an earlier one rejects messages that are perfectly valid, so a
         * check against the wrong version reports a result that has nothing to do with the stream.
         *
         * <p>Read back out of the stream's own {@code meta.protocolVersion} rather than from a
         * constant or the artifact version, so the envelope and the stream cannot disagree about
         * which release produced it.
         */
        public final String formatVersion;

        /**
         * Where to fetch the results: a path relative to this document.
         *
         * <p>Referenced rather than inlined because a Messages stream carries the feature sources and
         * so is far larger than this envelope, and because a consumer deciding whether it cares about
         * a report should not have to fetch the whole run to find out.
         */
        public final String location;

        /** Digest over the results payload as {@code sha256:<hex>}. */
        public final String digest;

        Results(String format, String formatVersion, String location, String digest) {
            this.format = format;
            this.formatVersion = formatVersion;
            this.location = location;
            this.digest = digest;
        }
    }
}
