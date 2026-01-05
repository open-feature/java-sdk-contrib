package dev.openfeature.contrib.providers.flagd.resolver.process;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class FlagsChangedResponse {

    @JsonProperty("success")
    private boolean success;

    @JsonProperty("changedFlags")
    private List<String> changedFlags;

    // Constructors
    public FlagsChangedResponse() {}

    public FlagsChangedResponse(boolean success, List<String> changedFlags) {
        this.success = success;
        this.changedFlags = changedFlags;
    }

    // Getters and Setters
    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public List<String> getChangedFlags() {
        return changedFlags;
    }

    public void setChangedFlags(List<String> changedFlags) {
        this.changedFlags = changedFlags;
    }

    @Override
    public String toString() {
        return "FlagsChangedResponse{" + "success=" + success + ", changedFlags=" + changedFlags + '}';
    }
}
