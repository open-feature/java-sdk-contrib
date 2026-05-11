package dev.openfeature.contrib.providers.flagd;

/**
 * !EXPERIMENTAL!
 * Controls whether JsonLogic targeting rules are compiled into Java bytecode for improved
 * evaluation performance. Compilation requires the {@code jdk.compiler} module at runtime,
 * which is typically not available in JRE-only images.
 * Compilation adds latency to the first evaluation of each rule (source generation, in-memory
 * javac, class loading); subsequent evaluations are faster. This trade-off favors long-running
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
     * Auto-detect: checks {@code javax.tools.ToolProvider.getSystemJavaCompiler() != null} at
     * initialization. If the compiler is available, compilation is enabled; otherwise the
     * interpreter is used silently. This is the default.
     */
    AUTO
}
