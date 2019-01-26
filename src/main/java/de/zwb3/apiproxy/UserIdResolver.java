package de.zwb3.apiproxy;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.Weigher;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import net.jcip.annotations.GuardedBy;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Resolves twitch usernames into twitch user IDs using Twitch API v5.
 */
public class UserIdResolver {

    /**
     * Client API to make requests with.
     */
    private final String clientId;

    /**
     * Time of the last errored user ID lookup.
     */
    @GuardedBy("this")
    @Nullable
    private volatile Instant lastExceptionTime;

    /**
     * Last exception (will pretty much always be an {@link ExecutionException})
     */
    @GuardedBy("this")
    @Nullable
    private Throwable lastException;

    /**
     * @param clientId Client API to make requests with.
     */
    public UserIdResolver(String clientId) {
        this.clientId = clientId;
    }

    /**
     * Caches username -> userid mapping.
     */
    private final LoadingCache<String, Optional<Long>> userIdCache =
            CacheBuilder.newBuilder()
                    // weighs entries in bytes
                    // note that each entry has a implementation-dependendant overhead,
                    // which is why the "512KiB" maximum should be taken with a big grain of salt
                    // in reality this cache should be expected to take 10 MiB of memory absolutely max.
                    .weigher((Weigher<String, Optional<Long>>) (key, value) -> key.length() + 8)
                    .maximumWeight(512 * 1024) // 512KiB, TODO config
                    .expireAfterWrite(7, TimeUnit.DAYS)
                    .build(new CacheLoader<String, Optional<Long>>() {
                        @Override
                        public Optional<Long> load(String loginName) throws Exception {

                            // https://dev.twitch.tv/docs/v5/reference/users/#get-users
                            // https://dev.twitch.tv/docs/v5/#translating-from-user-names-to-user-ids

                            // this errors when there is some connection or protocol error,
                            // or if the response is not valid JSON.
                            HttpResponse<JsonNode> jsonResponse = Unirest.get("https://api.twitch.tv/kraken/users")
                                    .queryString("login", loginName)
                                    .header("Accept", "application/vnd.twitchtv.v5+json")
                                    .header("Client-ID", clientId)
                                    .asJson();

                            JSONObject responseObject = jsonResponse.getBody().getObject();

                            // separate explicit handling for bad client IDs (to return error code 400).
                            // very defensive programming regarding the JSON, since it could technically be anything.
                            if (jsonResponse.getStatus() == 400 &&
                                    responseObject != null &&
                                    responseObject.has("message") &&
                                    responseObject.get("message") instanceof String) {

                                // sample response:
                                // {"error":"Bad Request","status":400,"message":"Invalid client id specified"}
                                String errorMessage = responseObject.getString("message");

                                if (errorMessage.equals("No client id specified") ||
                                        errorMessage.equals("Invalid client id specified")) {
                                    // Bad Request
                                    throw new BadClientIDException("Supplied Client-ID " + clientId + " is invalid or empty!");
                                }
                            }

                            // generic bad response code handling
                            if (jsonResponse.getStatus() != 200) {
                                throw new IOException(String.format("Bad Twitch response - %d %s",
                                        jsonResponse.getStatus(), jsonResponse.getStatusText()));
                            }

                            // at this point, the validation made sure that we got valid JSON
                            // + we got a response code of 200.
                            JSONArray userResponseList = responseObject.getJSONArray("users");
                            if (userResponseList.length() < 1) {
                                // user name could not be mapped to any user ID (invalid username/not found)
                                return Optional.empty();
                            }

                            return Optional.of(Long.parseLong(userResponseList.getJSONObject(0).getString("_id")));

                        }
                    });


    /**
     * Translates a twitch login name into its corresponding user ID.
     *
     * @param username Twitch login name to translate.
     * @return An optional that contains the user ID, if the username was found. An empty optional otherwise.
     * @throws ExecutionException If there was an error querying the username from the API.
     */
    public Optional<Long> translateUsername(String username) throws ExecutionException {
        try {
            return userIdCache.get(username);
        } catch (Exception e) {
            synchronized(this) {
                lastExceptionTime = Instant.now();
                lastException = e.getCause();
            }
            throw e;
        }
    }

    /**
     * @return the time and throwable of the last exception that occurred during user ID lookup.
     */
    public synchronized Pair<Instant, Throwable> getLastException() {
        return new ImmutablePair<>(lastExceptionTime, lastException);
    }

    /**
     * @return The current (approximate) amount of mappings in the cache.
     * @see LoadingCache#size()
     */
    public long getCacheCount() {
        return this.userIdCache.size();
    }

}