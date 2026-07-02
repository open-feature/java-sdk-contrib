package dev.openfeature.contrib.hooks.codereadiness;

import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.Hook;
import dev.openfeature.sdk.HookContext;
import dev.openfeature.sdk.ImmutableMetadata;
import dev.openfeature.sdk.exceptions.GeneralError;
import java.util.Map;
import org.semver4j.Semver;

public class SemVerComparator implements VersionComparator {
  @Override
  public boolean compare(String currentVersion, String minCodeVersion) throws Exception {
    String currentFormatted = currentVersion != null && currentVersion.startsWith("v") ? currentVersion : "v" + currentVersion;
    String minCodeVersionFormatted = minCodeVersion != null && minCodeVersion.startsWith("v") ? minCodeVersion : "v" + minCodeVersion;

    Semver currentSemver = Semver.parse(currentFormatted);
    if (currentSemver == null) {
      throw new IllegalArgumentException(String.format("invalid current semver: \"%s\"", currentVersion));
    }

    Semver minCodeVersionSemver = Semver.parse(minCodeVersionFormatted);
    if (minCodeVersionSemver == null) {
      throw new IllegalArgumentException(String.format("invalid min code version semver: \"%s\"", minCodeVersion)); 
    }

    return currentSemver.isGreaterThan(minCodeVersionSemver) || currentSemver.isEqualTo(minCodeVersionSemver);
  }
}
