package dev.openfeature.contrib.hooks.codereadiness;

/**
 * Defines the contract for parsing version strings and comparing code version objects
 * (current and minimum required version) according to specified rules. Used by {@link CodeReadinessHook}.
 *
 * <p>The {@link CodeReadinessHook} uses {@link SemVerComparator} by default for standard Semantic
 * Versioning, but developers may implement this interface to support custom or non-standard
 * versioning schemes.
 *
 * @param <T> The domain object type representing a parsed version (e.g., Semver, LocalDate, Integer).
 */
public interface VersionComparator<T> {

    /**
     * Parse version string into domain object.
     */
    T parse(String versionString) throws Exception;

    /**
     * Compare current version with required version.
     */
    boolean compare(T currentVersion, T minCodeVersion) throws Exception;
}
