package dev.openfeature.contrib.tools.tck;

import java.util.Locale;

/** Derives the names a run of the suite is identified by. */
final class ReportNames {

    /** Suite class name suffixes stripped when deriving a name. */
    private static final String[] SUITE_SUFFIXES = {"TckTest", "TCKTest", "TckSuite", "Test", "IT"};

    /** Used when a name sanitises to nothing, e.g. for an anonymous class. */
    private static final String FALLBACK = "tck";

    private ReportNames() {}

    /**
     * Derives a configuration name from a suite class, e.g. {@code MyProviderInProcessTest} becomes
     * {@code my-provider-in-process}.
     *
     * @param suite the concrete suite class
     * @return a hyphenated, lower-case configuration name
     */
    static String configurationOf(Class<?> suite) {
        String simple = suite.getSimpleName();
        for (String suffix : SUITE_SUFFIXES) {
            if (simple.length() > suffix.length() && simple.endsWith(suffix)) {
                simple = simple.substring(0, simple.length() - suffix.length());
                break;
            }
        }
        String hyphenated = simple.replaceAll("([a-z0-9])([A-Z])", "$1-$2")
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1-$2")
                .toLowerCase(Locale.ROOT);
        return hyphenated.isEmpty() ? FALLBACK : hyphenated;
    }
}
