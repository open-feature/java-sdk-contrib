package dev.openfeature.contrib.providers.v2.gofeatureflag.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.openfeature.contrib.providers.v2.gofeatureflag.GoFeatureFlagProviderOptions;
import dev.openfeature.contrib.providers.v2.gofeatureflag.api.bean.OfrepRequest;
import dev.openfeature.contrib.providers.v2.gofeatureflag.api.bean.OfrepResponse;
import dev.openfeature.contrib.providers.v2.gofeatureflag.bean.ExporterRequest;
import dev.openfeature.contrib.providers.v2.gofeatureflag.bean.Flag;
import dev.openfeature.contrib.providers.v2.gofeatureflag.bean.FlagConfigResponse;
import dev.openfeature.contrib.providers.v2.gofeatureflag.bean.GoFeatureFlagResponse;
import dev.openfeature.contrib.providers.v2.gofeatureflag.bean.IEvent;
import dev.openfeature.contrib.providers.v2.gofeatureflag.bean.Rule;
import dev.openfeature.contrib.providers.v2.gofeatureflag.exception.InvalidEndpoint;
import dev.openfeature.contrib.providers.v2.gofeatureflag.exception.InvalidOptions;
import dev.openfeature.contrib.providers.v2.gofeatureflag.util.Const;
import dev.openfeature.contrib.providers.v2.gofeatureflag.validator.Validator;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.exceptions.GeneralError;
import dev.openfeature.sdk.exceptions.InvalidContextError;
import dev.openfeature.sdk.exceptions.OpenFeatureError;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import okhttp3.ConnectionPool;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

@Slf4j
public class GoFeatureFlagApi {
    private static final ObjectMapper requestMapper = new ObjectMapper();
    private static final ObjectMapper responseMapper =
            new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /** apiKey contains the token to use while calling GO Feature Flag relay proxy. */
    private final String apiKey;

    /** httpClient is the instance of the OkHttpClient used by the provider. */
    private final OkHttpClient httpClient;

    /** parsedEndpoint is the endpoint of the GO Feature Flag relay proxy. */
    private final HttpUrl parsedEndpoint;
    private int counter = 0;


    /**
     * GoFeatureFlagController is the constructor of the controller to contact the GO Feature Flag
     * relay proxy.
     *
     * @param options - options to initialise the controller
     * @throws InvalidOptions - if the options are invalid
     */
    @Builder
    private GoFeatureFlagApi(final GoFeatureFlagProviderOptions options) throws InvalidOptions {
        Validator.ProviderOptions(options);
        this.apiKey = options.getApiKey();
        this.parsedEndpoint = HttpUrl.parse(options.getEndpoint());
        if (this.parsedEndpoint == null) {
            throw new InvalidEndpoint();
        }

        // Register JavaTimeModule to be able to deserialized java.time.Instant Object
        requestMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        requestMapper.enable(SerializationFeature.INDENT_OUTPUT);
        requestMapper.registerModule(new JavaTimeModule());

        int timeout = options.getTimeout() == 0 ? 10000 : options.getTimeout();
        long keepAliveDuration = options.getKeepAliveDuration() == null ? 7200000 : options.getKeepAliveDuration();
        int maxIdleConnections = options.getMaxIdleConnections() == 0 ? 1000 : options.getMaxIdleConnections();

        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(timeout, TimeUnit.MILLISECONDS)
                .readTimeout(timeout, TimeUnit.MILLISECONDS)
                .callTimeout(timeout, TimeUnit.MILLISECONDS)
                .readTimeout(timeout, TimeUnit.MILLISECONDS)
                .writeTimeout(timeout, TimeUnit.MILLISECONDS)
                .connectionPool(new ConnectionPool(maxIdleConnections, keepAliveDuration, TimeUnit.MILLISECONDS))
                .build();
    }

    /**
     * evaluateFlag is calling the GO Feature Flag relay proxy to evaluate the feature flag.
     *
     * @param key               - name of the flag
     * @param evaluationContext - context of the evaluation
     * @return EvaluationResponse with the evaluation of the flag
     * @throws OpenFeatureError - if an error occurred while evaluating the flag
     */
    public GoFeatureFlagResponse evaluateFlag(String key, EvaluationContext evaluationContext)
            throws OpenFeatureError {
        try {
            HttpUrl url = this.parsedEndpoint
                    .newBuilder()
                    .addEncodedPathSegment("ofrep")
                    .addEncodedPathSegment("v1")
                    .addEncodedPathSegment("evaluate")
                    .addEncodedPathSegment("flags")
                    .addEncodedPathSegment(key)
                    .build();

            val context = OfrepRequest.builder().context(evaluationContext.asObjectMap()).build();
            Request.Builder reqBuilder = new Request.Builder()
                    .url(url)
                    .addHeader(Const.HTTP_HEADER_CONTENT_TYPE, Const.APPLICATION_JSON)
                    .post(RequestBody.create(
                            requestMapper.writeValueAsBytes(context),
                            MediaType.get("application/json; charset=utf-8")));

            if (this.apiKey != null && !this.apiKey.isEmpty()) {
                reqBuilder.addHeader(Const.HTTP_HEADER_AUTHORIZATION, Const.BEARER_TOKEN + this.apiKey);
            }

            try (Response response = this.httpClient.newCall(reqBuilder.build()).execute()) {
                if (response.code() == HttpURLConnection.HTTP_UNAUTHORIZED
                        || response.code() == HttpURLConnection.HTTP_FORBIDDEN) {
                    throw new GeneralError("authentication/authorization error");
                }

                ResponseBody responseBody = response.body();
                String body = responseBody != null ? responseBody.string() : "";
                val goffResp = responseMapper.readValue(body, OfrepResponse.class);

                if (response.code() == HttpURLConnection.HTTP_BAD_REQUEST) {
                    throw new InvalidContextError("Invalid context: " + goffResp.getErrorDetails());
                }

                if (response.code() == HttpURLConnection.HTTP_INTERNAL_ERROR) {
                    throw new GeneralError("Unknown error while retrieving flag " + goffResp.getErrorDetails());
                }

                return goffResp.toGoFeatureFlagResponse();
            }
        } catch (IOException e) {
            throw new GeneralError("unknown error while retrieving flag " + key, e);
        }
    }

    public FlagConfigResponse retrieveFlagConfiguration(String etag) {
        this.counter++;
        if (this.counter > 2) {
            val flags = new HashMap<String, Flag>();
            val flag1 = new Flag();
            val variations = new HashMap<String, Object>();
            variations.put("enabled", true);
            variations.put("disabled", false);
            flag1.setVariations(variations);
            val defaultRule = new Rule();
            defaultRule.setName("default");
            defaultRule.setVariation("disabled");
            flag1.setDefaultRule(defaultRule);
            flags.put("TEST", flag1);

            val f = new FlagConfigResponse();
            f.setEtag("random etag2");
            f.setFlags(flags);
            f.setLastUpdated(new Date());
            return f;
        }

        System.out.println("retrieveFlagConfiguration");
        val flags = new HashMap<String, Flag>();
        val flag1 = new Flag();
        val variations = new HashMap<String, Object>();
        variations.put("enabled", true);
        variations.put("disabled", false);
        flag1.setVariations(variations);
        val defaultRule = new Rule();
        defaultRule.setName("default");
        defaultRule.setVariation("disabled");
        flag1.setDefaultRule(defaultRule);
        flags.put("TEST", flag1);

        val f = new FlagConfigResponse();
        f.setEtag("random etag");
        f.setFlags(flags);
        f.setLastUpdated(new Date());
        return f;
    }

    /**
     * sendEventToDataCollector is calling the GO Feature Flag data/collector api to store the flag
     * usage for analytics.
     *
     * @param eventsList - list of the event to send to GO Feature Flag
     */
    public void sendEventToDataCollector(List<IEvent> eventsList, Map<String, Object> exporterMetadata) {
        try {
            ExporterRequest events = new ExporterRequest(eventsList, exporterMetadata);
            HttpUrl url = this.parsedEndpoint
                    .newBuilder()
                    .addEncodedPathSegment("v1")
                    .addEncodedPathSegment("data")
                    .addEncodedPathSegment("collector")
                    .build();

            Request.Builder reqBuilder = new Request.Builder()
                    .url(url)
                    .addHeader(Const.HTTP_HEADER_CONTENT_TYPE, Const.APPLICATION_JSON)
                    .post(RequestBody.create(
                            requestMapper.writeValueAsBytes(events), MediaType.get("application/json; charset=utf-8")));

            if (this.apiKey != null && !this.apiKey.isEmpty()) {
                reqBuilder.addHeader(Const.HTTP_HEADER_AUTHORIZATION, Const.BEARER_TOKEN + this.apiKey);
            }

            try (Response response = this.httpClient.newCall(reqBuilder.build()).execute()) {
                if (response.code() == HttpURLConnection.HTTP_UNAUTHORIZED) {
                    throw new GeneralError("Unauthorized");
                }
                if (response.code() >= HttpURLConnection.HTTP_BAD_REQUEST) {
                    throw new GeneralError("Bad request: " + response.body());
                }
                if (response.code() == HttpURLConnection.HTTP_OK) {
                    log.info("Published {} events successfully: {}", eventsList.size(), response.body());
                }
            } catch (IOException e) {
                throw new GeneralError("Impossible to send the usage data to GO Feature Flag", e);
            }
        } catch (JsonProcessingException e) {
            throw new GeneralError("Impossible to convert data collector events", e);
        }
    }

}
