# Twitch API v3 Proxy [![Build Status](https://travis-ci.org/zwb3/twitch-api-v3-proxy.svg?branch=master)](https://travis-ci.org/zwb3/twitch-api-v3-proxy)

Provides continued support to projects using Twitch API v3 by providing the legacy API
at a local endpoint.

## Install Java 8

Java 8 is required, older releases are not supported.

On Debian, for example:

    sudo apt update
    sudo apt install openjdk-8-jre-headless
    java -version

Other distributions - google yourself!

## Download

Download ready-made artifacts that work on all platforms here:
https://github.com/zwb3/twitch-api-v3-proxy/releases/tag/release

You can use these downloads instead of building the project
yourself if your only aim is to run the project.

I recommend downloading the `twitch-api-v3-proxy-boot.tar`
(or zip if you prefer) for a full package including
start scripts.

## Unzip

For a fresh install:

    wget https://github.com/zwb3/twitch-api-v3-proxy/releases/download/release/twitch-api-v3-proxy-boot.tar
    sudo mkdir /opt/twitch-api-v3-proxy
    sudo tar xvf twitch-api-v3-proxy-boot.tar \
      -C /opt/twitch-api-v3-proxy \
      --strip-components=1

If you are upgrading (where you dont want to override the configuration file):

    wget https://github.com/zwb3/twitch-api-v3-proxy/releases/download/release/twitch-api-v3-proxy-boot.tar
    sudo tar xvf twitch-api-v3-proxy-boot.tar \
      -C /opt/twitch-api-v3-proxy \
      --strip-components=1 \
      --exclude='twitch-api-v3-proxy-boot/application.properties'

## Configure

If you downloaded a dist zip/tar, the application.properties
will be included inside the zip/tar. If you are building from source,
copy it from `./src/dist/application.properties`.

Edit `application.properties` with your own client ID.
This client ID is used to look up usernames to user IDs.
For the actual API requests, the proxy passes on your existing sent
`Authorization` header as-is.

You can also set a different host and address to have the webserver listen on.

## Run

    cd /opt/twitch-api-v3-proxy
    # run as www-data for security reasons!
    sudo -u www-data ./bin/twitch-api-v3-proxy

## systemd service file

Example systemd is provided in the repo as `twitch-api-v3-proxy.service`.

Install the service:

    sudo wget "https://raw.githubusercontent.com/zwb3/twitch-api-v3-proxy/release/twitch-api-v3-proxy.service" \
      -O "/etc/systemd/system/twitch-api-v3-proxy.service"
    sudo systemctl daemon-reload
    sudo systemctl enable twitch-api-v3-proxy
    sudo systemctl start twitch-api-v3-proxy
    # check the status
    sudo systemctl status twitch-api-v3-proxy

## Usage with pajbot1

If you are planning on using this proxy service with pajbot1,
update your pajbot1 with the latest patches from:
https://github.com/pajlada/pajbot

## Usage

Change your legacy APIv3 applications to make requests to
`http://127.0.0.1:7221/kraken` instead of
`https://api.twitch.tv/kraken`. The API will handle the
translation 100% transparently.

You can also print a simple status message (for example
with a chatbot) by querying `http://127.0.0.1:7221/apiproxy/status`,
which returns a plain text response like this:

> twitch-api-v3-proxy online for 10H6M49.308S,
> 2 usernames in cache, 2016 requests served!

## Build

Ensure you have a Java 8 JDK installed, and run:

    ./gradlew build

On windows, run:

    gradlew build

Note you do not need to build the project on the target machine,
you can simply copy the result artifact that you built on your
local machine to your server without any problems.

## Known endpoint differences

The following endpoints are known to behave differently when using
the v3 API proxy (because the v5 response format is different):

(Check that your application is not using them, otherwise you
have to patch these differences in your application!)

  -     GET /videos/:id
    
    [v3 doc](https://github.com/justintv/Twitch-API/blob/9991b5673734916ad8f06a5b6843e0da4b68ed9f/v3_resources/videos.md#get-videosid),
    [v5 doc](https://dev.twitch.tv/docs/v5/reference/videos/#get-video)
    
    `rootObject["preview"]` is different, use `rootObject["preview"]["medium"]`
    to get the original behaviour.

## How it works

Following is an overview of how the proxy code works:

First, the router considers up application-specific endpoints like `/apiproxy/status`.
If none of the more specific endpoints match, the request is processed by the
generic proxy end-point (see `ApiResponseController#proxyTwitchApi`).

The controller method passes the main workload on to the class `RouteMapper`.
The method `RouteMapper#mapApiPath` takes in the request method (`GET`) and
path `/kraken/channels/forsen`. Its job is to return a mapped path that
exists on the Twitch API v5, i.e. `/kraken/channels/22484632`. (Username was
replacd by user ID.)

The application has a list of "known routes", i.e. it knows about these routes
from the v3 API and it knows what parts of a route are variable, which parts
are usernames and which parts are constant.

These routes are represented by `ApiRoute` objects, with the stored information
being the request method (`GET`) and route segments
(`kraken`, `channels`, `:channelName`).

The known routes are loaded by the class `ApiRoutes`, which reads the file
`/de/zwb3/apiproxy/routes` from the classpath. (This file can be found in
`./src/main/java/resources/de/zwb3/apiproxy/routes`). See the class
documentation on that class and the file itself for more information
about the file format.

With this information, the `RouteMapper#mapApiPath` method first tries to find
a matching route fitting to the input method and route, by looking for routes
with the same amount of segments, same method and matching segment values
(constant segments must match, variable segments can take on any value).

Once it has found a matching route, it knows what segments are to be taken as-is
and which ones need to be translated from username to user ID. For example,
if the input route was `GET /kraken/channels/forsen`, it will have matched
the known route `GET /kraken/channels/:channelName`, and the method
will now build the resulting API path by translating `forsen` into the
corresponding user ID `22484632`.

The username -> user ID translation is performed by the class `UserIdResolver`,
which is initialized with the client ID from the config file.
This class contains a google guava (this is a Java library) `LoadingCache`,
which performs caching with up to 512 KiB of cache size and entries expiring
after 7 days. (hardcoded)

The method will return its mapped API path (`/kraken/channels/22484632`),
and the proxy endpoint (`ApiResponseController#proxyTwitchApi`) will now
proceed with making the twitch API request and proxying the response back.
It will copy **all** input request headers over to the proxy request
as-is, except for any `Accept` (because this needs to be a different value
for Twitch API v5), `Host` (for obvious reasons, this is different
for `api.twitch.tv` rather than `127.0.0.1:7221`) and `Content-Length`
headers. On `POST` requests, the sent message body will also be sent
on the proxy request as-is (byte-streamed).

The proxy will then copy all response headers and the status code back into 
its own response itself and afterwards start byte-streaming the twitch 
API response back. The API does not decode the twitch request itself, 
it just streams the raw bytes from the request to the original request.

## Notes about behaviour

If the mapper function cannot find any matching route, it will simply pass the
request on without replacing any elements in the route path.

## Error handling

Unfortunately, this API cannot do anything else than to behave differently
from the API v3 in case a username fails to resolve to a user ID.

In this case, it will return a response with a HTTP status code 404
and a JSON body, like this (not beautified though):

    {
       "error":"Not Found",
       "status":404,
       "message":"Username xd at segment :channel (#2) could not be translated: user not found"
    }

This response attempts to mimic the error responses produced
by the normal twitch API as well (same JSON format).

## Security

This application is designed to run as a local service **only**
and not meant to be exposed on the internet. For this reason,
the default listen address is `127.0.0.1` to prevent access from other
machines.
