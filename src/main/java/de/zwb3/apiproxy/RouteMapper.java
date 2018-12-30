package de.zwb3.apiproxy;

import com.google.common.collect.ImmutableList;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

/**
 * Maps a Twitch API v3 route to an API v5 route.
 */
public class RouteMapper {

    /**
     * list of known API v3 routes that can be translated to v5 endpoints
     */
    private final ImmutableList<ApiRoute> knownRoutes;

    /**
     * service to resolve usernames to user IDs
     */
    private final UserIdResolver userIdResolver;

    /**
     * @param knownRoutes    list of known API v3 routes that can be translated to v5 endpoints
     * @param userIdResolver service to resolve usernames to user IDs
     */
    public RouteMapper(List<ApiRoute> knownRoutes, UserIdResolver userIdResolver) {
        this.knownRoutes = ImmutableList.copyOf(knownRoutes);
        this.userIdResolver = userIdResolver;
    }

    /**
     * transform the given request path into the correct API v5 path.
     *
     * @param httpMethod The HTTP method that was used to make the incoming request.
     * @param inputPath  The path part of the request sent to the local server, e.g.
     *                   {@code /kraken/channels/forsen} for the full request URI
     *                   {@code http://localhost:8080/kraken/channels/forsen?client_id=auhuih273zf2f823}
     * @return The corresponding API path on the Twitch API v5, e.g. {@code /kraken/channels/22484632}
     * @throws IllegalArgumentException If the given requestURI could not be mapped to any known endpoint.
     * @throws ExecutionException       If there was an error translating a username into a user id.
     * @throws NoSuchUserException      If a username in the request URI could not be translated because the
     *                                  username is unknown.
     */
    public String mapAPIPath(String httpMethod, String inputPath) throws ExecutionException, NoSuchUserException {
        // split the given path at all forward slashes.
        // Then try to find the origin route that this is requesting.
        String[] inputSegments = StringUtils.split(inputPath, '/');

        Optional<ApiRoute> foundApiRoute = tryMatchRoute(httpMethod, inputSegments);

        if (foundApiRoute.isPresent()) {
            // this builds the new request route, with usernames replaced by user IDs.

            StringBuilder builder = new StringBuilder();
            ApiRoute apiRoute = foundApiRoute.get();
            List<String> apiRouteSegments = apiRoute.getSegments();
            // this api route has the exact same amount of segments as the input request URI.
            // which is why the segments array can be accessed without any further checks.
            for (int i = 0; i < apiRouteSegments.size(); i++) {
                String routeSegment = apiRouteSegments.get(i);
                String inputSegment = inputSegments[i];

                // append a forward slash before every segment
                // to get an output string like /kraken/channels/22484632
                builder.append('/');

                if (routeSegment.startsWith(":")) {
                    // this segment is a username. Translate to twitch user id.
                    Optional<Long> optionalUserId = userIdResolver.translateUsername(inputSegment);
                    if (!optionalUserId.isPresent()) {
                        throw new NoSuchUserException("Username " + inputSegment +
                                " at segment " + routeSegment +
                                " (#" + i + ") could not be translated: user not found");
                    }
                    long userId = optionalUserId.get();
                    builder.append(userId);
                } else if (routeSegment.startsWith("!")) {
                    // this is a non-translated variable, e.g. "!team". Copy the input to the output.
                    builder.append(inputSegment);
                } else {
                    // in this case routeSegment == inputSegment. Which one we take doesn't matter.
                    builder.append(routeSegment);
                }
            }
            return builder.toString();
        } else {
            // could not find a matching API endpoint. Try our best by just forwarding the
            // request without replacing any values.
            return inputPath;
        }
    }

    /**
     * Tries to find a matching route for the given URI segments.
     *
     * @param httpMethod The HTTP method that was used to make the incoming request.
     * @param segments   the input segments, e.g. "kraken", "channel", "forsen"
     * @return If found, an api route that matches this request. An empty optional otherwise.
     */
    private Optional<ApiRoute> tryMatchRoute(String httpMethod, String[] segments) {
        routeLoop:
        for (ApiRoute knownRoute : knownRoutes) {
            // segment count does not match. Skip this route.
            if (!(knownRoute.getSegmentCount() == segments.length)) {
                continue;
            }

            // http method does not match. Skip this route.
            if (!Objects.equals(httpMethod, knownRoute.getHttpMethod())) {
                continue;
            }

            // compare all segments.
            for (int i = 0; i < segments.length; i++) {
                if (!knownRoute.matches(segments[i], i)) {
                    // skip this route, because a segment did not match.
                    continue routeLoop;
                }
            }
            // found a matching route. return immediately.
            return Optional.of(knownRoute);
        }

        // could not find a matching route
        return Optional.empty();
    }

    public ImmutableList<ApiRoute> getKnownRoutes() {
        return knownRoutes;
    }

    public UserIdResolver getUserIdResolver() {
        return userIdResolver;
    }
}
