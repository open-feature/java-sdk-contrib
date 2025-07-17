package dev.openfeature.contrib.providers.ofrep.internal;

import java.net.http.HttpHeaders;
import lombok.Getter;

/**
 * Resolution class encapsulates the response from the OFREP server, along with additional fields.
 */
@Getter
public class Resolution {
    private final int responseStatus;
    private final HttpHeaders headers;
    private final OfrepResponse response;

    /**
     * Constructs a Resolution object with the given response status, headers, and response body.
     *
     * @param responseStatus - The HTTP response status code.
     * @param headers        - The HTTP headers from the response.
     * @param response       - The parsed response body as an OfrepResponse object.
     */
    public Resolution(int responseStatus, HttpHeaders headers, OfrepResponse response) {
        this.responseStatus = responseStatus;
        this.headers = headers;
        this.response = response.copy();
    }

    public OfrepResponse getResponse() {
        return response.copy();
    }
}
