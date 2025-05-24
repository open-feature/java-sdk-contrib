package dev.openfeature.contrib.providers.gofeatureflag.api.bean;

import dev.openfeature.contrib.providers.gofeatureflag.bean.IEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;

/** Events data. */
@Getter
public class ExporterRequest {
    /**
     * meta contains the metadata of the events to be sent along the events.
     */
    private final Map<String, Object> meta = new HashMap<>();

    /**
     * list of events to be sent to the data collector to collect the evaluation data.
     */
    private final List<IEvent> events;

    /**
     * Constructor.
     *
     * @param events           - list of events to be sent to the data collector to collect the evaluation data.
     * @param exporterMetadata - metadata of the events to be sent along the events.
     */
    public ExporterRequest(List<IEvent> events, Map<String, Object> exporterMetadata) {
        this.events = new ArrayList<>(events);
        if (exporterMetadata != null) {
            this.meta.putAll(exporterMetadata);
        }
    }
}
