package dev.openfeature.contrib.hooks.codereadiness;

/**
 * Defines the contract for comparing code version strings (current and minimum required
 * version) according to specified rules. Used by {@link CodeReadinessHook}.
 *
 * <p>The {@link CodeReadinessHook} uses {@link SemVerComparator} by default for standard Semantic
 * Versioning, but developers may implement this interface to support custom or non-standard
 * versioning schemes.
 */
public interface VersionComparator {
  /**
   * Compare current version with required version.
   *
   * @param currentVersion of the application
   * @param minCodeVersion required minimum version
   * @return true if currentVersion is greater than or equal to minCodeVersion
   * @throws Exception if there is a parsing error
   */
  boolean compare(String currentVersion, String minCodeVersion) throws Exception;
}