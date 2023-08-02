package dev.openfeature.contrib.providers.gofeatureflag;

import com.google.common.cache.CacheBuilder;
import dev.openfeature.contrib.providers.gofeatureflag.exception.InvalidEndpoint;
import dev.openfeature.contrib.providers.gofeatureflag.exception.InvalidOptions;
import dev.openfeature.contrib.providers.gofeatureflag.exception.InvalidTargetingKey;
import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.EventDetails;
import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.ImmutableMetadata;
import dev.openfeature.sdk.MutableContext;
import dev.openfeature.sdk.MutableStructure;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.ProviderEvent;
import dev.openfeature.sdk.Reason;
import dev.openfeature.sdk.Value;
import dev.openfeature.sdk.exceptions.FlagNotFoundError;
import dev.openfeature.sdk.exceptions.GeneralError;
import dev.openfeature.sdk.exceptions.TypeMismatchError;
import lombok.SneakyThrows;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import static dev.openfeature.contrib.providers.gofeatureflag.GoFeatureFlagProvider.CACHED_REASON;
import static org.junit.jupiter.api.Assertions.*;

class GoFeatureFlagProviderRealTest {
    private HttpUrl baseUrl;
    private MutableContext evaluationContext;

    private final static ImmutableMetadata defaultMetadata =
            ImmutableMetadata.builder()
                    .addString("pr_link", "https://github.com/thomaspoignant/go-feature-flag/pull/916")
                    .addInteger("version", 1)
                    .build();

    @BeforeEach
    void beforeEach() throws IOException {
        this.baseUrl = HttpUrl.parse("http://localhost:1031/");
        this.evaluationContext = new MutableContext();
        this.evaluationContext.setTargetingKey("d45e303a-38c2-11ed-a261-0242ac120002");
        this.evaluationContext.add("email", "john.doe@gofeatureflag.org");
        this.evaluationContext.add("firstname", "john");
        this.evaluationContext.add("lastname", "doe");
        this.evaluationContext.add("anonymous", false);
        this.evaluationContext.add("professional", true);
        this.evaluationContext.add("rate", 3.14);
        this.evaluationContext.add("age", 30);
        this.evaluationContext.add("company_info", new MutableStructure().add("name", "my_company").add("size", 120));
        List<Value> labels = new ArrayList<>();
        labels.add(new Value("pro"));
        labels.add(new Value("beta"));
        this.evaluationContext.add("labels", labels);
    }



    @Test
    void should_resolve_a_valid_boolean_flag_with_TARGETING_MATCH_reason() throws InvalidOptions, ExecutionException, InterruptedException {
        GoFeatureFlagProvider g = new GoFeatureFlagProvider(
                GoFeatureFlagProviderOptions.builder()
                        .endpoint(this.baseUrl.toString())
                        .timeout(1000)
                        .enableCache(true)
                        .flushIntervalMs(1000L)
                        .build());
        OpenFeatureAPI.getInstance().setProvider("test", g);
        Client cli = OpenFeatureAPI.getInstance().getClient("test");

        CompletableFuture<EventDetails> completableFuture = new CompletableFuture<>();
        OpenFeatureAPI.getInstance().onProviderReady(new Consumer<EventDetails>() {
            @Override
            public void accept(EventDetails eventDetails) {
                completableFuture.complete(eventDetails);
            }
        });
        OpenFeatureAPI.getInstance().onProviderError(new Consumer<EventDetails>() {
            @Override
            public void accept(EventDetails eventDetails) {
                completableFuture.complete(eventDetails);
            }
        });
        completableFuture.get();

//
//
//
//        FlagEvaluationDetails<Boolean> res = cli.getBooleanDetails("bool_targeting_match", false, this.evaluationContext);
//        assertEquals(true, res.getValue());
//        assertNull(res.getErrorCode());
//        assertEquals(Reason.TARGETING_MATCH.toString(), res.getReason());
//        assertEquals("True", res.getVariant());
//
//        cli.getBooleanDetails("bool_targeting_match", false, this.evaluationContext);
//        cli.getBooleanDetails("bool_targeting_match", false, this.evaluationContext);
//        cli.getBooleanDetails("bool_targeting_match", false, this.evaluationContext);
//        cli.getBooleanDetails("bool_targeting_match", false, this.evaluationContext);
//        cli.getBooleanDetails("bool_targeting_match", false, this.evaluationContext);
//
//
        Thread.sleep(1000);
//        OpenFeatureAPI.getInstance().shutdown();

    }

}
