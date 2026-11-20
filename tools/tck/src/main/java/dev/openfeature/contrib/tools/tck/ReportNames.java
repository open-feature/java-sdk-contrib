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

    /**
     * Turns a configuration name into the base name the envelope and the results stream share.
     * Anything not obviously path-safe becomes a hyphen, so a configuration named {@code flagd/rpc}
     * cannot write outside the report directory.
     *
     * @param configuration the configuration name
     * @return a file name stem, with no extension
     */
    static String baseNameOf(String configuration) {
        StringBuilder safe = new StringBuilder(configuration.length());
        for (int i = 0; i < configuration.length(); i++) {
            char c = configuration.charAt(i);
            boolean allowed = (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '-'
                    || c == '_'
                    || c == '.';
            safe.append(allowed ? c : '-');
        }
        String trimmed = trim(safe.toString());
        return trimmed.isEmpty() ? FALLBACK : trimmed;
    }

    private static String trim(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && (value.charAt(start) == '-' || value.charAt(start) == '.')) {
            start++;
        }
        while (end > start && (value.charAt(end - 1) == '-' || value.charAt(end - 1) == '.')) {
            end--;
        }
        return value.substring(start, end);
    }
}
