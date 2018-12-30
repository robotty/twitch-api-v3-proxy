package de.zwb3.apiproxy;

import com.google.common.collect.ImmutableList;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Objects;

/**
 * Describes a generic twitch API v3 route, e.g.
 * {@code /kraken/users/:username/follows/channels/:channelname}
 */
public class ApiRoute {

    /**
     * The HTTP method that this route uses, e.g. "GET", "PUT", "DELETE", etc.
     */
    private final String httpMethod;

    /**
     * The segments of this route, e.g. "kraken", "channels", ":channel"
     */
    private final ImmutableList<String> segments;

    /**
     * @param httpMethod  The HTTP method that this route uses, e.g. "GET", "PUT", "DELETE", etc.
     * @param routeFormat This is a string separating all the route segments with / forward slashes.
     *                    If a route segment begins with ":", e.g. ":username", it is treated as username variable.
     *                    In that case, all values will be accepted to exist in that segment.
     *                    Those values will be translated by the route mapping function into user IDs and replaced
     *                    to form the output API route.
     *                    If a route segment begins with "!", e.g. "!team", it is treated as an arbitrary
     *                    non-username variable. All values will be accepted in this place and the value
     */
    public ApiRoute(String httpMethod, String routeFormat) {
        this.httpMethod = httpMethod;
        this.segments = splitIntoSegments(routeFormat);
    }

    /**
     * Splits the given string by {@code /} and returns an immutable list with the segments.
     *
     * @param routeFormat A route string.
     * @return The route segments, as a list.
     */
    private static ImmutableList<String> splitIntoSegments(String routeFormat) {
        String[] segments = StringUtils.split(routeFormat, '/');
        return ImmutableList.copyOf(segments);
    }

    /**
     * Get whether a given segment matches the corresponding segment in this ApiRoute.
     *
     * @param segment      The segment, e.g. "channel" (index 1) for the URI "/channel/forsen".
     * @param segmentIndex The segment index, starting with 0 for the segment after the first slash.
     * @return true if the segment matches (equals the segment name if this segment is not variable on this ApiRoute,
     * or always returns true if this segment is variable), false otherwise.
     * @throws IndexOutOfBoundsException If the given segmentIndex is out of bounds for this ApiRoute.
     */
    public boolean matches(String segment, int segmentIndex) {
        // get our segment
        String ourSegment = segments.get(segmentIndex);
        // if our segment starts with : or !, its a variable, and accepts any value. Return true.
        if (ourSegment.startsWith(":") || ourSegment.startsWith("!")) {
            return true;
        }

        // this segment is part of the route path, e.g. "channel", and must match the given segment name.
        return Objects.equals(segment, ourSegment);
    }

    /**
     * @return The HTTP method that this route uses, e.g. "GET", "PUT", "DELETE", etc.
     */
    public String getHttpMethod() {
        return httpMethod;
    }

    /**
     * @return A immutable list of segments of this route.
     */
    public List<String> getSegments() {
        return segments;
    }

    /**
     * @return Return the amount of segments of this api route.
     */
    public int getSegmentCount() {
        return segments.size();
    }
}
