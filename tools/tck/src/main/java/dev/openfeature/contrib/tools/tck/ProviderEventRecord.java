package dev.openfeature.contrib.tools.tck;

import dev.openfeature.sdk.EventDetails;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * A provider event observed by a scenario's event handler, tagged with the Gherkin word that
 * registered the handler ({@code ready}, {@code error}, {@code stale}, {@code change}).
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP",
        justification = "The SDK's event payload is held and handed on as-is; copying it would "
                + "hide exactly the object under assertion")
public final class ProviderEventRecord {

    private final String type;
    private final EventDetails details;

    /**
     * Records an observed event.
     *
     * @param type the Gherkin event word the handler was registered under
     * @param details the event payload delivered by the SDK
     */
    public ProviderEventRecord(String type, EventDetails details) {
        this.type = type;
        this.details = details;
    }

    /**
     * Returns the Gherkin event word.
     *
     * @return the event type word
     */
    public String type() {
        return type;
    }

    /**
     * Returns the event payload delivered by the SDK.
     *
     * @return the event details
     */
    public EventDetails details() {
        return details;
    }
}
