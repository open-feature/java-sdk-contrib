package dev.openfeature.contrib.tools.tck;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * The envelope of one conformance run: what was tested, what the provider claims, and where the
 * results are. Per-scenario outcomes are deliberately not here; they live in the Cucumber Messages
 * stream that {@link Results} references.
 *
 * <p>Field names and nesting transcribe the report schema in the OpenFeature specification
 * repository, so every language's TCK emits the same document. Fields left {@code null} are omitted,
 * because the schema sets {@code additionalProperties: false}.
 *
 * @see <a href="https://github.com/open-feature/spec/issues/424">open-feature/spec#424</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ConformanceReport {

    /** The major version of the report schema this document conforms to, as an integer string. */
    public static final String SCHEMA_VERSION = "1";

    /** The schema version this document claims. */
    public final String schemaVersion;

    /** What was tested. */
    public final Provider provider;

    /** Which OpenFeature SDK the provider was exercised through. */
    public final Sdk sdk;

    /** What asked the questions, and which questions. */
    public final Tck tck;

    /** What the provider was pointed at. Always present, as the schema requires. */
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

        /** What the provider calls itself through its own metadata, not the suite name. */
        public final String name;

        /** The language the provider is written in, always {@code java} here. */
        public final String language;

        /** Which configuration was tested; a provider with materially different modes gets a report per mode. */
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
         * The open-feature/spec commit the executed artifacts came from. It identifies what the stream
         * does not carry: the canonical flag set and the control API definition.
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
         * How the backend was driven: {@code http}, {@code in-process} or {@code custom}. Always
         * written, never inferred: it is what the control states through
         * {@link BackendControl#backendType()}.
         */
        public final BackendType backendType;

        Backend(String description, BackendType backendType) {
            this.description = description;
            this.backendType = backendType;
        }
    }

    /** The capability set this provider claims, as the Gherkin tags that gate them. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class Declaration {

        /**
         * Capabilities the provider declares, including the leading at-sign. It cannot be derived from
         * the results: a skipped scenario does not say whether the provider declined the capability.
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
         * The Cucumber Messages release the stream was produced against, read back from the stream's
         * own {@code meta.protocolVersion}. TCK implementations pin different releases, and validating
         * against the wrong schema gives a meaningless result.
         */
        public final String formatVersion;

        /** Where to fetch the results: a path relative to this document. Not inlined, as the stream is far larger. */
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
