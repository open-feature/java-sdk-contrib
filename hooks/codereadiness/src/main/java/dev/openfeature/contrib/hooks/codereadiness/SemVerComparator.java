package dev.openfeature.contrib.hooks.codereadiness;

import java.util.Objects;
import org.semver4j.Semver;

/**
 * Default comparator implementation for standard Semantic Versioning (SemVer).
 */
public class SemVerComparator implements VersionComparator<Semver> {

    public SemVerComparator() {}

    @Override
    public Semver parse(String versionString) {
        Objects.requireNonNull(versionString, "versionString cannot be null");
        Semver semver = Semver.parse(versionString);
        if (semver == null) {
            throw new IllegalArgumentException(String.format("invalid semver: \"%s\"", versionString));
        }
        return semver;
    }

    @Override
    public boolean compare(Semver currentVersion, Semver minCodeVersion) {
        Objects.requireNonNull(currentVersion, "currentVersion cannot be null");
        Objects.requireNonNull(minCodeVersion, "minCodeVersion cannot be null");
        return currentVersion.isGreaterThanOrEqualTo(minCodeVersion);
    }
}
