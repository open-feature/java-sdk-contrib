package dev.openfeature.contrib.providers.gofeatureflag.hook.events;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;

/** Events data. */
@Getter
public class Events {
    private final Map<String, Object> meta = new HashMap<>();
    private final List<Event> events;

    public Events(List<Event> events, Map<String, Object> exporterMetadata) {
        this.events = new ArrayList<>(events);
        this.meta.put("provider", "java");
        this.meta.put("openfeature", true);
        if (exporterMetadata != null) {
            this.meta.putAll(exporterMetadata);
        }
    }
}
