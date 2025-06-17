package dev.openfeature.contrib.providers.gofeatureflag.bean;

/**
 * This enum represents the type of evaluation that can be performed.
 *
 * <p>IN_PROCESS: The evaluation is done in the process of the application.
 * REMOTE: The evaluation is done on the edge (e.g. CDN or API).</p>
 */
public enum EvaluationType {
    IN_PROCESS,
    REMOTE
}
