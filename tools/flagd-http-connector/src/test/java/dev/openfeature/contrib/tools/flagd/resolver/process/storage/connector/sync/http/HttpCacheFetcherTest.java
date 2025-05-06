package dev.openfeature.contrib.tools.flagd.resolver.process.storage.connector.sync.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

public class HttpCacheFetcherTest {

    @Test
    public void testFirstRequestSendsNoCacheHeaders() throws Exception {
        HttpClient httpClientMock = mock(HttpClient.class);
        HttpRequest.Builder requestBuilderMock = mock(HttpRequest.Builder.class);
        HttpRequest requestMock = mock(HttpRequest.class);
        HttpResponse<String> responseMock = spy(HttpResponse.class);
        doReturn(HttpHeaders.of(new HashMap<>(), (a, b) -> true))
                .when(responseMock)
                .headers();

        when(requestBuilderMock.build()).thenReturn(requestMock);
        when(httpClientMock.send(eq(requestMock), any(HttpResponse.BodyHandler.class)))
                .thenReturn(responseMock);
        when(responseMock.statusCode()).thenReturn(200);

        HttpCacheFetcher fetcher = new HttpCacheFetcher();
        fetcher.fetchContent(httpClientMock, requestBuilderMock);

        verify(requestBuilderMock, never()).header(eq("If-None-Match"), anyString());
        verify(requestBuilderMock, never()).header(eq("If-Modified-Since"), anyString());
    }

    @Test
    public void testResponseWith200ButNoCacheHeaders() throws Exception {
        HttpClient httpClientMock = mock(HttpClient.class);
        HttpRequest.Builder requestBuilderMock = mock(HttpRequest.Builder.class);
        HttpRequest requestMock = mock(HttpRequest.class);
        HttpResponse<String> responseMock = mock(HttpResponse.class);
        HttpHeaders headers = HttpHeaders.of(Collections.emptyMap(), (a, b) -> true);
        when(requestBuilderMock.build()).thenReturn(requestMock);
        when(httpClientMock.send(eq(requestMock), any(HttpResponse.BodyHandler.class)))
                .thenReturn(responseMock);
        when(responseMock.statusCode()).thenReturn(200);
        when(responseMock.headers()).thenReturn(headers);

        HttpCacheFetcher fetcher = new HttpCacheFetcher();
        HttpResponse<String> response = fetcher.fetchContent(httpClientMock, requestBuilderMock);

        assertEquals(200, response.statusCode());

        HttpRequest.Builder secondRequestBuilderMock = mock(HttpRequest.Builder.class);
        when(secondRequestBuilderMock.build()).thenReturn(requestMock);

        fetcher.fetchContent(httpClientMock, secondRequestBuilderMock);

        verify(secondRequestBuilderMock, never()).header(eq("If-None-Match"), anyString());
        verify(secondRequestBuilderMock, never()).header(eq("If-Modified-Since"), anyString());
    }

    @Test
    public void testFetchContentReturnsHttpResponse() throws Exception {
        HttpClient httpClientMock = mock(HttpClient.class);
        HttpRequest.Builder requestBuilderMock = mock(HttpRequest.Builder.class);
        HttpRequest requestMock = mock(HttpRequest.class);
        HttpResponse<String> responseMock = spy(HttpResponse.class);
        doReturn(HttpHeaders.of(new HashMap<>(), (a, b) -> true))
                .when(responseMock)
                .headers();

        when(requestBuilderMock.build()).thenReturn(requestMock);
        when(httpClientMock.send(eq(requestMock), any(HttpResponse.BodyHandler.class)))
                .thenReturn(responseMock);
        when(responseMock.statusCode()).thenReturn(404);

        HttpCacheFetcher fetcher = new HttpCacheFetcher();
        HttpResponse<String> result = fetcher.fetchContent(httpClientMock, requestBuilderMock);

        assertEquals(responseMock, result);
    }

    @Test
    public void test200ResponseNoEtagOrLastModified() throws Exception {
        HttpClient httpClientMock = mock(HttpClient.class);
        HttpRequest.Builder requestBuilderMock = mock(HttpRequest.Builder.class);
        HttpRequest requestMock = mock(HttpRequest.class);
        HttpResponse<String> responseMock = spy(HttpResponse.class);
        doReturn(HttpHeaders.of(new HashMap<>(), (a, b) -> true))
                .when(responseMock)
                .headers();

        when(requestBuilderMock.build()).thenReturn(requestMock);
        when(httpClientMock.send(eq(requestMock), any(HttpResponse.BodyHandler.class)))
                .thenReturn(responseMock);
        when(responseMock.statusCode()).thenReturn(200);

        HttpCacheFetcher fetcher = new HttpCacheFetcher();
        fetcher.fetchContent(httpClientMock, requestBuilderMock);

        Field cachedETagField = HttpCacheFetcher.class.getDeclaredField("cachedETag");
        cachedETagField.setAccessible(true);
        assertNull(cachedETagField.get(fetcher));
        Field cachedLastModifiedField = HttpCacheFetcher.class.getDeclaredField("cachedLastModified");
        cachedLastModifiedField.setAccessible(true);
        assertNull(cachedLastModifiedField.get(fetcher));
    }

    @Test
    public void testUpdateCacheOn200Response() throws Exception {
        HttpClient httpClientMock = mock(HttpClient.class);
        HttpRequest.Builder requestBuilderMock = mock(HttpRequest.Builder.class);
        HttpRequest requestMock = mock(HttpRequest.class);
        HttpResponse<String> responseMock = spy(HttpResponse.class);
        doReturn(HttpHeaders.of(
                        Map.of(
                                "Last-Modified",
                                Arrays.asList("Wed, 21 Oct 2015 07:28:00 GMT"),
                                "ETag",
                                Arrays.asList("etag-value")),
                        (a, b) -> true))
                .when(responseMock)
                .headers();

        when(requestBuilderMock.build()).thenReturn(requestMock);
        when(httpClientMock.send(eq(requestMock), any(HttpResponse.BodyHandler.class)))
                .thenReturn(responseMock);
        when(responseMock.statusCode()).thenReturn(200);
        HttpCacheFetcher fetcher = new HttpCacheFetcher();
        fetcher.fetchContent(httpClientMock, requestBuilderMock);

        Field cachedETagField = HttpCacheFetcher.class.getDeclaredField("cachedETag");
        cachedETagField.setAccessible(true);
        assertEquals("etag-value", cachedETagField.get(fetcher));
        Field cachedLastModifiedField = HttpCacheFetcher.class.getDeclaredField("cachedLastModified");
        cachedLastModifiedField.setAccessible(true);
        assertEquals("Wed, 21 Oct 2015 07:28:00 GMT", cachedLastModifiedField.get(fetcher));
    }

    @Test
    public void testRequestWithCachedEtagIncludesIfNoneMatchHeader() throws Exception {
        HttpClient httpClientMock = mock(HttpClient.class);
        HttpRequest.Builder requestBuilderMock = mock(HttpRequest.Builder.class);
        HttpRequest requestMock = mock(HttpRequest.class);
        HttpResponse<String> responseMock = spy(HttpResponse.class);
        doReturn(HttpHeaders.of(Map.of("ETag", Arrays.asList("12345")), (a, b) -> true))
                .when(responseMock)
                .headers();

        when(requestBuilderMock.build()).thenReturn(requestMock);
        when(httpClientMock.send(eq(requestMock), any(HttpResponse.BodyHandler.class)))
                .thenReturn(responseMock);
        when(responseMock.statusCode()).thenReturn(200);

        HttpCacheFetcher fetcher = new HttpCacheFetcher();
        fetcher.fetchContent(httpClientMock, requestBuilderMock);
        fetcher.fetchContent(httpClientMock, requestBuilderMock);

        verify(requestBuilderMock, times(1)).header("If-None-Match", "12345");
    }

    @Test
    public void testNullHttpClientOrRequestBuilder() {
        HttpCacheFetcher fetcher = new HttpCacheFetcher();
        HttpRequest.Builder requestBuilderMock = mock(HttpRequest.Builder.class);

        assertThrows(NullPointerException.class, () -> {
            fetcher.fetchContent(null, requestBuilderMock);
        });

        assertThrows(NullPointerException.class, () -> {
            fetcher.fetchContent(mock(HttpClient.class), null);
        });
    }

    @Test
    public void testResponseWithUnexpectedStatusCode() throws Exception {
        HttpClient httpClientMock = mock(HttpClient.class);
        HttpRequest.Builder requestBuilderMock = mock(HttpRequest.Builder.class);
        HttpRequest requestMock = mock(HttpRequest.class);
        HttpResponse<String> responseMock = spy(HttpResponse.class);
        doReturn(HttpHeaders.of(new HashMap<>(), (a, b) -> true))
                .when(responseMock)
                .headers();

        when(requestBuilderMock.build()).thenReturn(requestMock);
        when(httpClientMock.send(eq(requestMock), any(HttpResponse.BodyHandler.class)))
                .thenReturn(responseMock);
        when(responseMock.statusCode()).thenReturn(500);

        HttpCacheFetcher fetcher = new HttpCacheFetcher();
        HttpResponse<String> response = fetcher.fetchContent(httpClientMock, requestBuilderMock);

        assertEquals(500, response.statusCode());
        verify(requestBuilderMock, never()).header(eq("If-None-Match"), anyString());
        verify(requestBuilderMock, never()).header(eq("If-Modified-Since"), anyString());
    }

    @Test
    public void testRequestIncludesIfModifiedSinceHeaderWhenLastModifiedCached() throws Exception {
        HttpClient httpClientMock = mock(HttpClient.class);
        HttpRequest.Builder requestBuilderMock = mock(HttpRequest.Builder.class);
        HttpRequest requestMock = mock(HttpRequest.class);
        HttpResponse<String> responseMock = spy(HttpResponse.class);
        doReturn(HttpHeaders.of(
                        Map.of("Last-Modified", Arrays.asList("Wed, 21 Oct 2015 07:28:00 GMT")), (a, b) -> true))
                .when(responseMock)
                .headers();

        when(requestBuilderMock.build()).thenReturn(requestMock);
        when(httpClientMock.send(eq(requestMock), any(HttpResponse.BodyHandler.class)))
                .thenReturn(responseMock);
        when(responseMock.statusCode()).thenReturn(200);

        HttpCacheFetcher fetcher = new HttpCacheFetcher();
        fetcher.fetchContent(httpClientMock, requestBuilderMock);
        fetcher.fetchContent(httpClientMock, requestBuilderMock);

        verify(requestBuilderMock).header(eq("If-Modified-Since"), eq("Wed, 21 Oct 2015 07:28:00 GMT"));
    }

    @Test
    public void testCalls200And304Responses() throws Exception {
        HttpClient httpClientMock = mock(HttpClient.class);
        HttpRequest.Builder requestBuilderMock = mock(HttpRequest.Builder.class);
        HttpRequest requestMock = mock(HttpRequest.class);
        HttpResponse<String> responseMock200 = mock(HttpResponse.class);
        HttpResponse<String> responseMock304 = mock(HttpResponse.class);

        when(requestBuilderMock.build()).thenReturn(requestMock);
        when(httpClientMock.send(eq(requestMock), any(HttpResponse.BodyHandler.class)))
                .thenReturn(responseMock200)
                .thenReturn(responseMock304);
        when(responseMock200.statusCode()).thenReturn(200);
        when(responseMock304.statusCode()).thenReturn(304);

        HttpCacheFetcher fetcher = new HttpCacheFetcher();
        fetcher.fetchContent(httpClientMock, requestBuilderMock);
        fetcher.fetchContent(httpClientMock, requestBuilderMock);

        verify(responseMock200, times(1)).statusCode();
        verify(responseMock304, times(2)).statusCode();
    }

    @Test
    public void testRequestIncludesBothEtagAndLastModifiedHeaders() throws Exception {
        HttpClient httpClientMock = mock(HttpClient.class);
        HttpRequest.Builder requestBuilderMock = mock(HttpRequest.Builder.class);
        HttpRequest requestMock = mock(HttpRequest.class);
        HttpResponse<String> responseMock = spy(HttpResponse.class);
        doReturn(HttpHeaders.of(new HashMap<>(), (a, b) -> true))
                .when(responseMock)
                .headers();

        when(requestBuilderMock.build()).thenReturn(requestMock);
        when(httpClientMock.send(eq(requestMock), any(HttpResponse.BodyHandler.class)))
                .thenReturn(responseMock);
        when(responseMock.statusCode()).thenReturn(200);

        HttpCacheFetcher fetcher = new HttpCacheFetcher();
        Field cachedETagField = HttpCacheFetcher.class.getDeclaredField("cachedETag");
        cachedETagField.setAccessible(true);
        cachedETagField.set(fetcher, "test-etag");
        Field cachedLastModifiedField = HttpCacheFetcher.class.getDeclaredField("cachedLastModified");
        cachedLastModifiedField.setAccessible(true);
        cachedLastModifiedField.set(fetcher, "test-last-modified");

        fetcher.fetchContent(httpClientMock, requestBuilderMock);

        verify(requestBuilderMock).header("If-None-Match", "test-etag");
        verify(requestBuilderMock).header("If-Modified-Since", "test-last-modified");
    }

    @SneakyThrows
    @Test
    public void testHttpClientSendExceptionPropagation() {
        HttpClient httpClientMock = mock(HttpClient.class);
        HttpRequest.Builder requestBuilderMock = mock(HttpRequest.Builder.class);
        HttpRequest requestMock = mock(HttpRequest.class);

        when(requestBuilderMock.build()).thenReturn(requestMock);
        when(httpClientMock.send(eq(requestMock), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Network error"));

        HttpCacheFetcher fetcher = new HttpCacheFetcher();
        assertThrows(IOException.class, () -> {
            fetcher.fetchContent(httpClientMock, requestBuilderMock);
        });
    }

    @Test
    public void testOnlyEtagAndLastModifiedHeadersCached() throws Exception {
        HttpClient httpClientMock = mock(HttpClient.class);
        HttpRequest.Builder requestBuilderMock = mock(HttpRequest.Builder.class);
        HttpRequest requestMock = mock(HttpRequest.class);
        HttpResponse<String> responseMock = spy(HttpResponse.class);
        doReturn(HttpHeaders.of(
                        Map.of(
                                "Last-Modified",
                                Arrays.asList("last-modified-value"),
                                "ETag",
                                Arrays.asList("etag-value")),
                        (a, b) -> true))
                .when(responseMock)
                .headers();

        when(requestBuilderMock.build()).thenReturn(requestMock);
        when(httpClientMock.send(eq(requestMock), any(HttpResponse.BodyHandler.class)))
                .thenReturn(responseMock);
        when(responseMock.statusCode()).thenReturn(200);

        HttpCacheFetcher fetcher = new HttpCacheFetcher();
        fetcher.fetchContent(httpClientMock, requestBuilderMock);

        verify(requestBuilderMock, never()).header(eq("Some-Other-Header"), anyString());
    }
}
