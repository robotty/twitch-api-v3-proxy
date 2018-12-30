package de.zwb3.apiproxy;

import com.google.common.collect.ImmutableList;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides the known list of routes as a list.
 */
public class ApiRoutes {

    private static final Logger log = LoggerFactory.getLogger(ApiRoutes.class);

    /**
     * Load the known list of routes from the classpath resource at /de/zwb3/apiproxy/routes
     * <p>
     * The file expects either empty lines or lines only consisting of whitespace,
     * lines beginning with a {@code #} character (those lines will be silently ignored as comments)
     * and lines formatted exactly {@code HTTPMETHOD /api/endpoint/:channel/example/!team},
     * with {@code :channel} and {@code !team} being variables.
     * <p>
     * Unexpectedly skipped lines will be printed to the logger.
     *
     * @return A list of api routes that were loaded
     * @throws IOException If an I/O error occurs while reading from the file
     */
    public static List<ApiRoute> getApiRoutes() throws IOException {
        URL routesFile = ApiRoutes.class.getResource("/de/zwb3/apiproxy/routes");
        if (routesFile == null) {
            throw new FileNotFoundException("/de/zwb3/apiproxy/routes file not found on classpath!");
        }

        List<ApiRoute> list = new ArrayList<>();
        try (InputStream inputStream = routesFile.openStream();
             BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
             InputStreamReader inputStreamReader = new InputStreamReader(bufferedInputStream, StandardCharsets.UTF_8);
             BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {

            // iterate lines in the routes file
            int lineId = 0;
            for (String line = bufferedReader.readLine(); line != null; line = bufferedReader.readLine()) {
                lineId++;
                line = line.trim();
                // # lines are treated as comments
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                // split at first space to separate method and api route path
                String[] split = StringUtils.split(line, " ", 2);
                if (split.length < 2) {
                    log.warn("Invalid line skipped in routes file: at line " + lineId + ": " + line);
                    continue;
                }

                String httpMethod = split[0];
                String routePath = split[1];

                list.add(new ApiRoute(httpMethod, routePath));
                log.debug("Successfully loaded route " + httpMethod + " " + routePath + " from line " + lineId);
            }

        }

        return ImmutableList.copyOf(list);
    }
}
