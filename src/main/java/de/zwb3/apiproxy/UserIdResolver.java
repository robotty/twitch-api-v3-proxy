package de.zwb3.apiproxy;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.Weigher;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import org.json.JSONArray;
import org.json.JSONObject;

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
     * @param clientId  Client API to make requests with.
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
                    .weigher((Weigher<String, Optional<Long>>) (key, value) -> key.length() + 8)
                    .maximumWeight(64 * 1024 * 1024) // 64 MB, TODO config
                    .expireAfterWrite(7, TimeUnit.DAYS)
                    .build(new CacheLoader<String, Optional<Long>>() {
                        @Override
                        public Optional<Long> load(String key) throws Exception {

                            // https://dev.twitch.tv/docs/v5/reference/users/#get-users
                            // https://dev.twitch.tv/docs/v5/#translating-from-user-names-to-user-ids

                            HttpResponse<JsonNode> jsonResponse = Unirest.get("https://api.twitch.tv/kraken/users")
                                    .queryString("login", key)
                                    .header("Accept", "application/vnd.twitchtv.v5+json")
                                    .header("Client-ID", clientId)
                                    .asJson();

                            JSONObject responseObject = jsonResponse.getBody().getObject();
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
     * @param username Twitch login name to translate.
     * @return An optional that contains the user ID, if the username was found. An empty optional otherwise.
     * @throws ExecutionException If there was an error querying the username from the API.
     */
    public Optional<Long> translateUsername(String username) throws ExecutionException {
        return userIdCache.get(username);
    }

    /**
     * @return The current (approximate) amount of mappings in the cache.
     * @see LoadingCache#size()
     */
    public long getCacheCount() {
        return this.userIdCache.size();
    }

}