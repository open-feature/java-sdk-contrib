package dev.openfeature.contrib.tools.providertck;

import java.util.Locale;

/**
 * Derives the names a conformance report is identified and filed under.
 *
 * <p>Kept apart from the report itself so that both the default
 * {@link ProviderTckHarness#configuration()} and the file the plugin writes agree on one derivation.
 */
final class ReportNames {

    /** Suffixes a suite class name carries for JUnit's benefit rather than the report's. */
    private static final String[] SUITE_SUFFIXES = {"TckTest", "TCKTest", "TckSuite", "Test", "IT"};

    /** Used when a name sanitises away to nothing, which an anonymous class manages. */
    private static final String FALLBACK = "provider-tck";

    private ReportNames() {}

    /**
     * Derives a configuration name from a suite class.
     *
     * <p>{@code FlagdInProcessTckTest} becomes {@code flagd-in-process}: the suffix that exists only
     * so JUnit picks the class up is dropped, and the rest is hyphenated. A provider whose modes do
     * not read well this way overrides {@link ProviderTckHarness#configuration()} and says so
     * directly.
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
     * Turns a configuration name into the file the report is written to.
     *
     * <p>Configuration names are chosen to read well in a failure message rather than to be
     * path-safe, so anything that is not obviously safe becomes a hyphen. Without this a
     * configuration named {@code flagd/rpc} would silently write outside the directory it was given.
     *
     * @param configuration the configuration name
     * @return a file name ending in {@code .json}
     */
    static String fileNameOf(String configuration) {
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
        return (trimmed.isEmpty() ? FALLBACK : trimmed) + ".json";
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
