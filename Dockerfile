FROM java:8-jre-alpine
ADD twitch-api-v3-proxy-boot.tar /srv/

RUN mkdir /etc/twitch-api-v3-proxy

WORKDIR /etc/twitch-api-v3-proxy

EXPOSE 7221

ENTRYPOINT ["/srv/twitch-api-v3-proxy-boot/bin/twitch-api-v3-proxy"]
