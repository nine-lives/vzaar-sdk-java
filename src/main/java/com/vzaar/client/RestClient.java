package com.vzaar.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vzaar.VzaarErrorList;
import com.vzaar.VzaarException;
import com.vzaar.VzaarServerException;
import com.vzaar.util.ObjectMapperFactory;
import com.vzaar.util.RequestParameterMapper;
import org.apache.http.Header;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class RestClient {
    private static final String HEADER_CLIENT_ID = "X-Client-Id";
    private static final String HEADER_AUTH_TOKEN = "X-Auth-Token";

    private final RequestParameterMapper parameterMapper = new RequestParameterMapper();
    private final ObjectMapper objectMapper = ObjectMapperFactory.make();
    private final RestClientConfiguration configuration;
    private final CloseableHttpClient httpClient;
    private final ThreadLocal<HttpClientContext> httpContext = new ThreadLocal<>();
    private final AtomicReference<Map<String, String>> responseHeadersReference = new AtomicReference<>();

    public RestClient(RestClientConfiguration configuration) {
        this.configuration = configuration;
        this.httpClient = makeHttpClient(configuration);
    }

    public Map<String, String> getLastResponseHeaders() {
        return responseHeadersReference.get();
    }

    public <T> Resource<T> resource(String resource, Class<T> type) {
        return new Resource<>(this, type).resource(resource);
    }

    String getEndpoint() {
        return configuration.getEndpoint();
    }

    <T> Resource<T> get(Resource<T> resource) {
        try {
            return execute(resource, new HttpGet(resource.getUri()));
        } catch (IOException | URISyntaxException e) {
            throw new VzaarException(e);
        }
    }

    <T> Resource<T> post(Resource<T> resource, Object payload) {
        try {
            return execute(resource, setPayload(new HttpPost(resource.getUri()), payload));
        } catch (IOException | URISyntaxException e) {
            throw new VzaarException(e);
        }
    }

    <T> Resource<T> patch(Resource<T> resource, Object payload) {
        try {
            return execute(resource, setPayload(new HttpPatch(resource.getUri()), payload));
        } catch (IOException | URISyntaxException e) {
            throw new VzaarException(e);
        }
    }

    <T> Resource<T> delete(Resource<T> resource) {
        try {
            return execute(resource, new HttpDelete(resource.getUri()));
        } catch (IOException | URISyntaxException e) {
            throw new VzaarException(e);
        }
    }

    RequestParameterMapper getParameterMapper() {
        return parameterMapper;
    }

    ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    private <T> Resource<T> execute(Resource<T> resource, HttpUriRequest request) throws IOException {
        request.addHeader(HEADER_CLIENT_ID, configuration.getClientId());
        request.addHeader(HEADER_AUTH_TOKEN, configuration.getAuthToken());

        try (CloseableHttpResponse response = httpClient.execute(request, getHttpContext())) {
            if (response.getStatusLine().getStatusCode() >= 400) {
                throwError(response);
            }

            responseHeadersReference.set(buildHeaderMap(response));
            return resource.body(readEntity(response));
        }
    }

    private <T extends HttpEntityEnclosingRequest> T setPayload(T request, Object payload) throws JsonProcessingException, UnsupportedEncodingException {
        StringEntity entity = new StringEntity(objectMapper.writeValueAsString(payload));
        entity.setContentType("application/json");
        request.setEntity(entity);
        return request;
    }

    private Map<String, String> buildHeaderMap(CloseableHttpResponse response) {
        Map<String, String> headers = new HashMap<>();
        for (Header header : response.getAllHeaders()) {
            headers.put(header.getName(), header.getValue());
        }
        return headers;
    }

    private void throwError(CloseableHttpResponse response) {
        try {
            byte[] content = readEntity(response);
            throw new VzaarServerException(
                    response.getStatusLine().getStatusCode(),
                    response.getStatusLine().getReasonPhrase(),
                    objectMapper.readValue(content, VzaarErrorList.class));
        } catch (IOException ignore) {
            throw new VzaarServerException(
                    response.getStatusLine().getStatusCode(),
                    response.getStatusLine().getReasonPhrase(),
                    new VzaarErrorList());
        }
    }

    private byte[] readEntity(CloseableHttpResponse response) throws IOException {
        if (response.getEntity() == null) {
            return null;
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        response.getEntity().writeTo(out);
        return out.toByteArray();
    }


    private CloseableHttpClient makeHttpClient(RestClientConfiguration configuration) {
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(configuration.getMaxConnectionsPerRoute() * 2);
        connectionManager.setDefaultMaxPerRoute(configuration.getMaxConnectionsPerRoute());
        return HttpClients.custom().setConnectionManager(connectionManager).build();
    }

    private HttpClientContext getHttpContext() {
        HttpClientContext context = httpContext.get();
        if (context == null) {
            context = HttpClientContext.create();
            httpContext.set(context);
        }
        return context;
    }
}
