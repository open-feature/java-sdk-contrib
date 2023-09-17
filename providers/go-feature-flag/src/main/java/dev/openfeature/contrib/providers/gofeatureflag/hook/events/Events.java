package dev.openfeature.contrib.providers.gofeatureflag.hook.events;

import lombok.Getter;

import java.util.*;

/**
 * Events data.
 */
@Getter
public class Events {
    private List<Event> events;

    private static Map<String, String> meta = new HashMap<>();

    static {
        meta.put("provider", "java");
        meta.put("openfeature", "true");
    }

    public Events(List<Event> events) {
        this.events = new ArrayList<>(events);
    }
}
