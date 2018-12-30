# Twitch API v3 Proxy

Provides continued support to projects using Twitch API v3 by providing the legacy API
at a local endpoint.

## Build

    ./gradlew build

## Configure

Edit `application.properties` with your own client ID.
This client ID is used to look up usernames to user IDs.
For the actual API requests, the proxy passes on your existing sent
`Authorization` header as-is.

You can also set host and address to have the webserver listen on.

## Run

    # place twitch-api-v3-proxy-boot.tar in an empty directory and run:
    tar xvz twitch-api-v3-proxy-boot.tar --strip-components=1
    ./bin/twitch-api-v3-proxy-boot

Make sure `application.properties` is present in the working directory.

## Usage

Change your legacy APIv3 applications to make requests to
`http://localhost:7221/kraken` instead of
`https://api.twitch.tv/kraken`. The API will handle the
translation 100% transparently.

You can also print a simple status message for example
with a chatbot by querying `http://localhost:7221/apiproxy/status`.

## systemd service file

Example systemd is provided in the repo as `apiproxy.service`.