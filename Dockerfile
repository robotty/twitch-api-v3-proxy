# First phase is for building the project
FROM gradle:5.4.1-jdk8-alpine as build

# Gradle Docker image runs as the "gradle" user
# which means Pepeeja permissions
RUN mkdir /home/gradle/project
WORKDIR /home/gradle/project

# Permissions
COPY --chown=gradle \
     . ./

# Don't start a daemon, that's a docker build
ENV GRADLE_OPTS=-Dorg.gradle.daemon=false

RUN gradle build

# Second phase, run on any base you want
FROM java:8-jre-alpine

ENV PROJECT_ROOT=/opt/twitch-api-v3-proxy-boot
ENV PATH=$PROJECT_ROOT:$PATH

# Type ./ instead of 20 symbols 4HEad
WORKDIR $PROJECT_ROOT

# Add files 1 by 1 since adding the tar/zip here uses up more space
COPY --from=build \
     /home/gradle/project/build/libs/twitch-api-v3-proxy.jar \
     ./lib/

COPY --from=build \
     /home/gradle/project/build/bootScripts/twitch-api-v3-proxy \
     ./bin/

WORKDIR /etc/twitch-api-v3-proxy

COPY --from=build \
     /home/gradle/project/src/dist/application.properties \
     ./

EXPOSE 7221

ENTRYPOINT ["/opt/twitch-api-v3-proxy-boot/bin/twitch-api-v3-proxy"]
