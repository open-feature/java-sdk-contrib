package dev.openfeature.contrib.providers.gofeatureflag.hook.events;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;

/** Events data. */
@Getter
public class Events {
    private static final Map<String, String> meta = new HashMap<>();

    static {
        meta.put("provider", "java");
        meta.put("openfeature", "true");
    }

    private final List<Event> events;

    public Events(List<Event> events) {
        this.events = new ArrayList<>(events);
    }
}
