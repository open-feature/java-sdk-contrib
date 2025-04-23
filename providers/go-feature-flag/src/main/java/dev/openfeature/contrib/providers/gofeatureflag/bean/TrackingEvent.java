package dev.openfeature.contrib.providers.gofeatureflag.bean;

import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TrackingEvent implements IEvent {
    /**
     * Kind for a feature event is feature.
     * A feature event will only be generated if the trackEvents attribute of the flag is set to true.
     */
    private String kind;

    /**
     * ContextKind is the kind of context which generated an event. This will only be "anonymousUser" for events
     * generated
     * on behalf of an anonymous user or the reserved word "user" for events generated on behalf of a non-anonymous
     * user
     */
    private String contextKind;

    /**
     * UserKey The key of the user object used in a feature flag evaluation. Details for the user object used in a
     * feature
     * flag evaluation as reported by the "feature" event are transmitted periodically with a separate index event.
     */
    private String userKey;

    /**
     * CreationDate When the feature flag was requested at Unix epoch time in milliseconds.
     */
    private Long creationDate;

    /**
     * Key of the event.
     */
    private String key;

    /**
     * EvaluationContext contains the evaluation context used for the tracking
     */
    private Map<String, Object> evaluationContext;

    /**
     * TrackingDetails contains the details of the tracking event
     */
    private Map<String, Object> trackingEventDetails;
}
