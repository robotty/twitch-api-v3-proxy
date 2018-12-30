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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.springframework.web.bind.annotation.RequestMethod.*;

@Controller
public class ApiResponseController {

    private static final Logger log = LoggerFactory.getLogger(ApiResponseController.class);

    private final RouteMapper mapper;

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

        // mappedPath is for example "/kraken/streams/22484632"
        String mappedPath = mapper.mapAPIPath(request.getMethod(), request.getRequestURI());

        URI requestURI = new URIBuilder()
                .setScheme("https")
                .setHost("api.twitch.tv")
                .setPath(mappedPath)
                .setQuery(request.getQueryString())
                .build();

        HttpUriRequest proxyRequest = RequestBuilder.create(request.getMethod())
                .setUri(requestURI)
                .addHeader("Accept", "application/vnd.twitchtv.v5+json")
                .build();

        // copy input headers to proxy request, except for "Accept" header
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();

            // don't proxy the Accept header, since we set our own Accept header for API v5.
            if ("Accept".equalsIgnoreCase(headerName) || "Host".equalsIgnoreCase(headerName)) {
                continue;
            }

            Enumeration<String> headerValues = request.getHeaders(headerName);
            while (headerValues.hasMoreElements()) {
                String headerValue = headerValues.nextElement();

                proxyRequest.addHeader(headerName, headerValue);
            }
        }

        HttpClient client = getHttpClient();
        HttpResponse proxyResponse = client.execute(proxyRequest);

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
             OutputStream outputStream = response.getOutputStream()) {
            IOUtils.copy(inputStream, outputStream);
        }

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
                mapper.getUserIdResolver().getCacheCount() + " usernames in cache";

        return statusLine;
    }
}
