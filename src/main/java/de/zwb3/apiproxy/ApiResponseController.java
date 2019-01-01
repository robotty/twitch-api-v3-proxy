package de.zwb3.apiproxy;

import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.web.bind.annotation.RequestMethod.*;

@Controller
public class ApiResponseController {

    private static final Logger log = LoggerFactory.getLogger(ApiResponseController.class);

    private final RouteMapper mapper;

    /**
     * This counter is incremented on every received request to the proxy endpoint.
     */
    private final AtomicLong requestCounter = new AtomicLong(0);

    @Autowired
    public ApiResponseController(@Value("${clientId}") String clientId) throws IOException {
        log.info("Initialized with clientId={}", clientId);

        List<ApiRoute> knownRoutes = ApiRoutes.getApiRoutes();
        UserIdResolver userIdResolver = new UserIdResolver(clientId);

        mapper = new RouteMapper(knownRoutes, userIdResolver);
    }

    private final ThreadLocal<HttpClient> proxyHttpClients = new ThreadLocal<>();

    // default endpoint, proxy to twitch
    @RequestMapping(value = "/**", method = {GET, HEAD, POST, PUT, PATCH, DELETE, OPTIONS, TRACE})
    public void proxyTwitchAPI(HttpServletRequest request, HttpServletResponse response) throws ExecutionException,
            NoSuchUserException, URISyntaxException, IOException {

        requestCounter.incrementAndGet();

        // mappedPath is for example "/kraken/streams/22484632"
        String mappedPath = mapper.mapApiPath(request.getMethod(), request.getRequestURI());

        URI proxyUri = new URIBuilder()
                .setScheme("https")
                .setHost("api.twitch.tv")
                .setPath(mappedPath)
                .setQuery(request.getQueryString())
                .build();

        // request is the received request, proxyUri is the URI to make the proxy request to.
        HttpResponse proxyResponse = makeProxyRequest(request, proxyUri);

        // copy status
        response.setStatus(proxyResponse.getStatusLine().getStatusCode());

        HeaderIterator proxyResponseHeaders = proxyResponse.headerIterator();
        // copy headers
        while (proxyResponseHeaders.hasNext()) {
            Header proxyResponseHeader = proxyResponseHeaders.nextHeader();
            response.addHeader(proxyResponseHeader.getName(), proxyResponseHeader.getValue());
        }

        // copy body
        HttpEntity responseEntity = proxyResponse.getEntity();
        try (InputStream inputStream = responseEntity.getContent();
             BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
             OutputStream outputStream = response.getOutputStream();
             BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(outputStream)) {
            IOUtils.copy(bufferedInputStream, bufferedOutputStream);
        }

    }

    /**
     * Make a proxy request similar to the given originalRequest, but to the given proxyUri.
     * @param originalRequest The request that was sent to this application.
     * @param proxyUri The URI to proxy to.
     * @return The response of the proxy request.
     * @throws IOException If an I/O exception occurs.
     */
    private HttpResponse makeProxyRequest(HttpServletRequest originalRequest, URI proxyUri) throws IOException {
        HttpResponse proxyResponse;
        try (InputStream inputStream = originalRequest.getInputStream();
             BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream)) {

            // If this is a POST request, copy the request body.
            InputStreamEntity entity;
            if ("POST".equalsIgnoreCase(originalRequest.getMethod())) {
                entity = new InputStreamEntity(bufferedInputStream);
            } else {
                entity = null;
            }

            HttpUriRequest proxyRequest = RequestBuilder.create(originalRequest.getMethod())
                    .setUri(proxyUri)
                    .addHeader("Accept", "application/vnd.twitchtv.v5+json")
                    .setEntity(entity)
                    .build();

            // copy input headers to proxy request, except for "Accept" header
            Enumeration<String> headerNames = originalRequest.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();

                // don't proxy the Accept header, since we set our own Accept header for API v5.
                if ("Accept".equalsIgnoreCase(headerName) ||
                        "Host".equalsIgnoreCase(headerName) ||
                        "Content-Length".equalsIgnoreCase(headerName)) {
                    continue;
                }

                Enumeration<String> headerValues = originalRequest.getHeaders(headerName);
                while (headerValues.hasMoreElements()) {
                    String headerValue = headerValues.nextElement();

                    proxyRequest.addHeader(headerName, headerValue);
                }
            }

            HttpClient client = getHttpClient();
            proxyResponse = client.execute(proxyRequest);
        }
        return proxyResponse;
    }

    /**
     * @return The http client for the current thread.
     */
    private HttpClient getHttpClient() {
        HttpClient client = proxyHttpClients.get();
        if (client == null) {
            client = HttpClients.createDefault();
            proxyHttpClients.set(client);
        }
        return client;
    }

    @RequestMapping(value = "/apiproxy/status", produces = MediaType.TEXT_PLAIN_VALUE)
    @ResponseBody
    public String statusMessage() {
        RuntimeMXBean rb = ManagementFactory.getRuntimeMXBean();
        // in milliseconds
        long uptime = rb.getUptime();
        Duration uptimeDuration = Duration.ofMillis(uptime);

        String statusLine = "twitch-api-v3-proxy online for " + uptimeDuration.toString().substring(2) + ", " +
                mapper.getUserIdResolver().getCacheCount() + " usernames in cache, " +
                requestCounter.get() + " requests served!";

        return statusLine;
    }
}
