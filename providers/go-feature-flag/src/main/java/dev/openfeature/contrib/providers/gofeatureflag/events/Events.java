package dev.openfeature.contrib.providers.gofeatureflag.events;

import lombok.Getter;

import java.util.*;

@Getter
public class Events {
    private List<Event> events = new LinkedList<>();
    private static Map<String, String> meta = new HashMap<>();
    static {
        meta.put("provider", "java");
        meta.put("openfeature", "true");
    }

    public Events(List<Event> events) {
        this.events = events;
    }
}
