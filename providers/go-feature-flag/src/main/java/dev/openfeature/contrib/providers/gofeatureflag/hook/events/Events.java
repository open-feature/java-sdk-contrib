package dev.openfeature.contrib.providers.gofeatureflag.hook.events;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;

/** Events data. */
@Getter
public class Events {
    /**
     * meta contains the metadata of the events to be sent along the events.
     */
    private final Map<String, Object> meta = new HashMap<>();

    /**
     * list of events to be sent to the data collector to collect the evaluation data.
     */
    private final List<Event> events;

    /**
     * Constructor.
     *
     * @param events - list of events to be sent to the data collector to collect the evaluation data.
     * @param exporterMetadata - metadata of the events to be sent along the events.
     */
    public Events(List<Event> events, Map<String, Object> exporterMetadata) {
        this.events = new ArrayList<>(events);
        this.meta.put("provider", "java");
        this.meta.put("openfeature", true);
        if (exporterMetadata != null) {
            this.meta.putAll(exporterMetadata);
        }
    }
}
