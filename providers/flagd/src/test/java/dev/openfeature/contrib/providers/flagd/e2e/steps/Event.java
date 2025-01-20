package dev.openfeature.contrib.providers.flagd.e2e.steps;

import dev.openfeature.sdk.EventDetails;

public class Event {
    public String type;
    public EventDetails details;

    public Event(String eventType, EventDetails eventDetails) {
        this.type = eventType;
        this.details = eventDetails;
    }
}
