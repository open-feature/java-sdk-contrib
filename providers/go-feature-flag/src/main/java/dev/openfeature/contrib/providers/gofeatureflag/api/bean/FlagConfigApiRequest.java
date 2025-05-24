package dev.openfeature.contrib.providers.gofeatureflag.api.bean;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Represents the request body for the flag configuration API.
 */
@Data
@AllArgsConstructor
public class FlagConfigApiRequest {
    private List<String> flags;
}
