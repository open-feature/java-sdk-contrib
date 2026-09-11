package dev.openfeature.contrib.tools.providertck;

import java.util.Locale;

/**
 * Derives the names a run of the suite is identified by.
 *
 * <p>Kept apart from any one consumer so that the default
 * {@link ProviderTckHarness#configuration()} and anything that files a run's output under that name
 * agree on one derivation.
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
}
