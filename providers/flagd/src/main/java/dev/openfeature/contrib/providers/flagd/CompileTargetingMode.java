package dev.openfeature.contrib.providers.flagd;

/**
 * !EXPERIMENTAL!
 * Controls whether JsonLogic targeting rules are compiled into Java bytecode for improved
 * evaluation performance. On Java 24+, the built-in Class-File API (JEP 484) is used, which works
 * on JRE-only images. On older JVMs, compilation requires the {@code jdk.compiler} module (javac),
 * which is typically not available in JRE-only images.
 * Compilation adds latency to the first evaluation of each rule (much less with the Class-File
 * API than with javac); subsequent evaluations are faster. This trade-off favors long-running
 * services where rules are evaluated many times.
 */
public enum CompileTargetingMode {
    /**
     * Always attempt compilation. Logs a warning if the compiler is unavailable.
     */
    ENABLED,

    /**
     * Never compile; always use the interpreter.
     */
    DISABLED,

    /**
     * Auto-detect: at initialization, checks whether any JsonLogic compiler (Class-File API or
     * javac) is available. If so, compilation is enabled; otherwise the interpreter is used
     * silently. This is the default.
     */
    AUTO
}
